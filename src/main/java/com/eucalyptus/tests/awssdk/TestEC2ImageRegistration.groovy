package com.eucalyptus.tests.awssdk

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DeregisterImageRequest
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.LaunchPermission
import com.amazonaws.services.ec2.model.LaunchPermissionModifications
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.amazonaws.services.ec2.model.RegisterImageRequest
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.github.sjones4.youcan.youserv.YouServ
import com.github.sjones4.youcan.youserv.YouServClient
import com.github.sjones4.youcan.youserv.model.ServiceCertificate
import com.google.common.io.BaseEncoding
import com.google.common.io.ByteStreams
import com.google.common.io.Closeables
import com.google.common.io.CountingInputStream
import com.google.common.io.CountingOutputStream
import com.google.common.io.Files
import com.google.common.primitives.Shorts
import org.junit.Assert
import org.junit.Test

import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream

import static com.eucalyptus.tests.awssdk.N4j.ACCESS_KEY
import static com.eucalyptus.tests.awssdk.N4j.EC2_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.S3_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.SECRET_KEY
import static com.eucalyptus.tests.awssdk.N4j.SERVICES_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.TOKENS_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.initEndpoints
import static com.eucalyptus.tests.awssdk.N4j.print

/**
 * This test covers uploading and registering an hvm instance-store image.
 */
class TestEC2ImageRegistration {

  private AWSCredentialsProvider credentials
  private String imageLocation = System.getProperty(
      'n4j.image.hvm-url',
      'http://cloud.centos.org/centos/7/images/CentOS-7-x86_64-GenericCloud.raw.tar.gz' )

  TestEC2ImageRegistration( ){
    initEndpoints( )
    this.credentials = new StaticCredentialsProvider( new BasicAWSCredentials( ACCESS_KEY, SECRET_KEY ) )
  }

  AmazonEC2 getEC2Client( AWSCredentialsProvider clientCredentials = credentials ) {
    final AmazonEC2Client ec2 = new AmazonEC2Client( clientCredentials )
    ec2.setEndpoint( EC2_ENDPOINT )
    ec2
  }

  AWSSecurityTokenService getSTSClient( AWSCredentialsProvider clientCredentials = credentials ) {
    final AWSSecurityTokenService sts = new AWSSecurityTokenServiceClient( clientCredentials )
    sts.setEndpoint( TOKENS_ENDPOINT )
    sts
  }

  AmazonS3 getS3Client( AWSCredentialsProvider clientCredentials = credentials ) {
    final AmazonS3Client s3 =
        new AmazonS3Client( clientCredentials, new ClientConfiguration( ).withSignerOverride("S3SignerType") )
    s3.setEndpoint( S3_ENDPOINT )
    s3.setS3ClientOptions( new S3ClientOptions( ).builder( )
        .setPathStyleAccess( S3_ENDPOINT.endsWith( '/services/objectstorage' ) )
        .build( ) )
    s3
  }

  YouServ getServicesClient( AWSCredentialsProvider clientCredentials = credentials ) {
    YouServClient youServ = new YouServClient( clientCredentials )
    youServ.setEndpoint( SERVICES_ENDPOINT )
    youServ
  }

  @SuppressWarnings("ChangeToOperator")
  @Test
  void testHvmImageRegistration( ) throws Exception {
    final List<Runnable> cleanupTasks = [] as List<Runnable>
    try {
      AmazonS3Client s3 = getS3Client()
      AmazonEC2 ec2 = getEC2Client()

      // get some cloud info
      print("Getting account number for credentials")
      String accountNumber = getSTSClient( ).getCallerIdentity( new GetCallerIdentityRequest( ) ).account
      print("Account number is ${accountNumber}")

      print("Getting ec2 service certificate")
      X509Certificate ec2Certificate = null
      getServicesClient( ).describeServiceCertificates( ).with {
        for ( ServiceCertificate cert : serviceCertificates ) {
          if ( 'compute' == cert.serviceType &&
              'pem' == cert.certificateFormat &&
              'image-bundling' == cert.certificateUsage ) {
            print( "Found ec2 certificate for image bundling : ${cert.certificateFingerprint}" )
            ec2Certificate = CertificateFactory.getInstance("X.509" )
                .generateCertificate( new ByteArrayInputStream( cert.certificate.getBytes(StandardCharsets.UTF_8) ) )
            break
          }
        }
        void
      }

      // get image
      String imageFileName = imageLocation.substring( imageLocation.lastIndexOf('/') + 1 )
      String imageEtagFileName = "${imageFileName}.etag"
      String imageName = imageFileName.substring( 0,
          Math.min( imageFileName.length(), imageFileName.indexOf('.' ) ) ).toLowerCase( )
      print("Using image name ${imageName}, file ${imageFileName}, etag file ${imageEtagFileName}")

      File imageFile = new File(N4j.cacheDir, imageFileName)
      File imageEtagFile = new File(N4j.cacheDir, imageEtagFileName)
      String imageEtag = null
      if ( imageEtagFile.exists( ) && imageFile.exists( ) ) {
        imageEtag = Files.toString( imageEtagFile, StandardCharsets.UTF_8 )
        print( "Image ${imageFileName} has etag ${imageEtag}" )
      }
      URL imageUrl = new URL( imageLocation )
      HttpURLConnection imageUrlConnection = (HttpURLConnection) imageUrl.openConnection()
      if ( imageEtag ) {
        imageUrlConnection.addRequestProperty( 'If-None-Match', imageEtag )
      }
      if ( imageUrlConnection.getResponseCode( ) == 304 ) {
        print( "Using cached image for ${imageLocation}" )
      } else {
        String urlEtag = imageUrlConnection.getHeaderField( 'ETag' )
        if ( urlEtag ) {
          print( "Downloading image with etag ${urlEtag}" )
          Files.asCharSink(imageEtagFile, StandardCharsets.UTF_8).write(urlEtag)
        } else {
          print( "Downloading image with no etag, caching disabled" )
          imageEtagFile.delete( )
        }
        print( "Downloading image to file ${imageFileName} ${imageLocation}" )
        BufferedOutputStream imageOut = new BufferedOutputStream( new FileOutputStream( imageFile ) )
        ByteStreams.copy( imageUrlConnection.getInputStream( ), imageOut )
        imageOut.close( )
      }

      // start upload
      String bucketName = imageName.replace('_','-').replaceAll( '[^0-9a-z-]', '' )
      print("Creating bucket for image upload ${bucketName}")
      s3.createBucket(bucketName)

      long size = imageFile.length()
      long partSize = 10485760 // 10MB
      long encryptedSize = ( size % 16 == 0 ) ? size : ( ( size.div(16).intValue( ) ) + 1 ) * 16
      long parts = encryptedSize / partSize
      if (parts * partSize < encryptedSize) parts++
      print("Uploading image of size ${size} (encrypted size ${encryptedSize}) in ${parts} parts")

      def partName = { int partNumber ->
        "${imageName}.part.${partNumber < 10 ? "0" + partNumber : String.valueOf(partNumber)}"
      }
      SecureRandom random = SecureRandom.getInstance( "SHA1PRNG" )
      byte[] key = new byte[16]; random.nextBytes( key )
      byte[] iv  = new byte[16]; random.nextBytes( iv )

      Cipher bulkCipher = Cipher.getInstance( "AES/CBC/PKCS5Padding" )
      bulkCipher.init( Cipher.ENCRYPT_MODE, new SecretKeySpec( key, 'AES' ), new IvParameterSpec( iv ), random )

      MessageDigest sha1 = MessageDigest.getInstance("SHA-1")
      String sha1Digest = ''
      Map<Integer,String> partSha1Digests = [:]
      long uncompressedSize = 0
      imageFile.withInputStream { fileIn ->
        fileIn.mark( 16 )
        final byte[] gzipHeaderBytes = new byte[2]
        fileIn.read( gzipHeaderBytes )
        if ( GZIPInputStream.GZIP_MAGIC != (0xFFFF & Shorts.fromBytes( gzipHeaderBytes[1], gzipHeaderBytes[0] ) ) ) {
          Assert.fail( "Image not in gzip format : ${imageLocation}" )
        }
        fileIn.reset( )

        TarHeaderSizeOutputStream tarHeaderSizeOutputStream =
            new TarHeaderSizeOutputStream( ByteStreams.nullOutputStream( ) )
        DigestOutputStream digestOutputStream = new DigestOutputStream( tarHeaderSizeOutputStream, sha1 )
        UncompressedSizeInputStream uncompressedSizeInputStream =
            new UncompressedSizeInputStream( new BufferedInputStream( fileIn ), digestOutputStream )
        CipherInputStream encryptingInputStream = new CipherInputStream( uncompressedSizeInputStream, bulkCipher )
        InputStream uploadDataInputStream = new UncloseableInputStream( encryptingInputStream )

        for (int part = 0; part < parts; part++) {
          long partLength = Math.min( partSize, encryptedSize-(part*partSize) )
          print( "Uploading part ${bucketName}/${partName(part)} size ${partLength}" )
          MessageDigest partSha1 = MessageDigest.getInstance("SHA-1")
          DigestInputStream partDigestInputStream =
              new DigestInputStream( ByteStreams.limit( uploadDataInputStream, partLength ), partSha1 )
          s3.putObject(
              new PutObjectRequest(
                  bucketName,
                  partName(part),
                  partDigestInputStream,
                  new ObjectMetadata( contentLength: partLength )
              ).withCannedAcl(CannedAccessControlList.AwsExecRead) )
          partSha1Digests.put( part, BaseEncoding.base16( ).lowerCase( ).encode( partSha1.digest( ) ) )
        }

        uncompressedSize = uncompressedSizeInputStream.size
        print( "Unzipped image size ${uncompressedSize}" )
        if ( tarHeaderSizeOutputStream.size ) {
          uncompressedSize = tarHeaderSizeOutputStream.size
          print( "Using image uncompressed size from tar header ${uncompressedSize}"  )
        } else {
          print( "WARNING tar header size not found, using uncompressed file size ${uncompressedSize}"  )
        }
        sha1Digest = BaseEncoding.base16( ).lowerCase( ).encode( sha1.digest( ) )
      }
      print( "Image uncompressed size ${uncompressedSize}, sha-1 digest ${sha1Digest}" )

      Cipher cipher = Cipher.getInstance( 'RSA/ECB/PKCS1Padding' )
      cipher.init(Cipher.ENCRYPT_MODE, ec2Certificate.getPublicKey( ), random)

      // create and upload manifest
      print( "Generating manifest" )
      StringBuilder manifestBuilder = new StringBuilder( 8 * 1024 )
      manifestBuilder.append( """\
      <?xml version="1.0" encoding="ASCII"?>
      <manifest>
        <version>2007-10-10</version>
        <bundler>
          <name>n4j-image-registration-test</name>
          <version>1</version>
          <release>0</release>
        </bundler>
        <machine_configuration>
          <architecture>x86_64</architecture>
        </machine_configuration>
        <image>
          <name>${imageName}</name>
          <user>${accountNumber}</user>
          <type>machine</type>
          <digest algorithm="SHA1">${sha1Digest}</digest>
          <size>${String.valueOf(uncompressedSize)}</size>
          <bundled_size>${String.valueOf(encryptedSize)}</bundled_size>
          <ec2_encrypted_key algorithm="AES-128-CBC">${BaseEncoding.base16().lowerCase().encode(cipher.doFinal(BaseEncoding.base16().lowerCase().encode(key).getBytes(StandardCharsets.UTF_8)))}</ec2_encrypted_key>
          <ec2_encrypted_iv>${BaseEncoding.base16().lowerCase().encode(cipher.doFinal(BaseEncoding.base16().lowerCase().encode(iv).getBytes(StandardCharsets.UTF_8)))}</ec2_encrypted_iv>
          <user_encrypted_key algorithm="AES-128-CBC"/>
          <user_encrypted_iv/>
          <parts count="${parts}">
      """.stripIndent( ) )

      for (int part = 0; part < parts; part++) {
        manifestBuilder.append( """\
        <part index="${part}">
          <filename>${partName(part)}</filename>
          <digest algorithm="SHA1">${partSha1Digests.get(part)}</digest>
        </part>\n""" )
      }

      manifestBuilder.append( '''\
          </parts>
        </image>
        <signature>UNSIGNED</signature>
      </manifest>
      '''.stripIndent( ) )
      String manifestName = "${imageName}.manifest.xml"
      String manifest = manifestBuilder.toString( )
      print( "Uploading generated manifest as ${manifestName}:\n${manifest}" )
      byte[] manifestBytes = manifest.getBytes(StandardCharsets.UTF_8)
      InputStream manifestIn = new ByteArrayInputStream(manifestBytes)
      ObjectMetadata manifestMetadata = new ObjectMetadata()
      manifestMetadata.setContentType('text/plain')
      manifestMetadata.setContentLength(manifestBytes.length)
      s3.putObject(new PutObjectRequest(bucketName, manifestName, manifestIn, manifestMetadata)
          .withCannedAcl(CannedAccessControlList.AwsExecRead))

      // register image
      ec2.describeImages( new DescribeImagesRequest(
          filters: [
              new Filter( name: 'name', values: [ imageName ] )
          ]
      ) ).with {
        String emi = images?.getAt(0)?.imageId
        if ( emi ) {
          print( "Deregistering existing image ${emi} with name ${imageName}" )
          ec2.deregisterImage( new DeregisterImageRequest(
              imageId: emi
          ) )
        }
      }
      print( "Registering uploaded image ${bucketName}/${manifestName}" )
      String emi = ec2.registerImage( new RegisterImageRequest(
          imageLocation: "${bucketName}/${manifestName}",
          architecture: 'x86_64',
          name: imageName,
          virtualizationType: 'hvm'
      ) ).with {
        imageId
      }
      print( "Registered image id ${emi}" )

      // set-up image
      print( "Setting launch permissions for ${emi}" )
      ec2.modifyImageAttribute( new ModifyImageAttributeRequest(
          imageId: emi,
          //attribute: 'launchPermission',
          launchPermission: new LaunchPermissionModifications(
            add: [
                new LaunchPermission(
                    group: 'all'
                )
            ]
          )
      ) )

      print( "Test complete" )
    } finally {
      // Attempt to clean up anything we created
      cleanupTasks.reverseEach { Runnable cleanupTask ->
        try {
          cleanupTask.run()
        } catch ( Exception e ) {
          e.printStackTrace()
        }
      }
    }
  }

  class UncloseableInputStream extends FilterInputStream {
    protected UncloseableInputStream(final InputStream inputStream) {
      super(inputStream)
    }

    @Override
    void close() throws IOException {
      // no
    }
  }

  class TarHeaderSizeOutputStream extends FilterOutputStream {
    private long size = 0

    TarHeaderSizeOutputStream(final OutputStream out) {
      super(out)
    }

    long getSize( ) {
      return size
    }

    @Override
    void write(final int b) throws IOException {
      throw new IOException( "write(int) not supported" )
    }

    @Override
    void write(final byte[] b, final int off, final int len) throws IOException {
      out.write(b, off, len)
      if ( size == 0 ) { // read uncompressed size from tar header
        final byte[] sizeBytes = new byte[12]
        System.arraycopy( b, off+124, sizeBytes,0, 12 )
        size = new BigInteger( sizeBytes ).longValue( )
      }
    }
  }

  class UncompressedSizeInputStream extends FilterInputStream {
    private final CountingOutputStream countingOutputStream
    private final InflaterOutputStream inflaterOutputStream
    private boolean readHeader = false

    protected UncompressedSizeInputStream( final InputStream inputStream, final OutputStream out ) {
      super( inputStream )
      countingOutputStream = new CountingOutputStream( out )
      inflaterOutputStream = new InflaterOutputStream(
          countingOutputStream,
          new Inflater( true )
      )
    }

    long getSize( ) {
      inflaterOutputStream.close( )
      countingOutputStream.count
    }

    @Override
    int read() throws IOException {
      throw new IOException( "read() not supported" )
    }

    @Override
    int read(final byte[] b, final int off, final int len) throws IOException {
      int read = super.read(b, off, len)
      if ( readHeader ) {
        if ( read > 0 ) inflaterOutputStream.write( b, off, read )
      } else { // strip gzip header
        CountingInputStream countingInputStream = new CountingInputStream(
            new ByteArrayInputStream( b, off, read )
        )
        try {
          GZIPInputStream gzipInputStream = new GZIPInputStream( countingInputStream )
          int headerByteCount = countingInputStream.count
          Closeables.close( gzipInputStream, true )
          inflaterOutputStream.write( b, headerByteCount, read - headerByteCount )
          readHeader = true
        } catch ( Exception e ) {
          throw new IOException( e )
        }
      }
      return read
    }

    @Override
    void close() throws IOException {
      Closeables.close( inflaterOutputStream, true )
      super.close( )
    }
  }
}
