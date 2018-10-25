
To run 4.4 nephoria for java (n4j) tests against a eucalyptus cloud controller use:

```
  docker run --rm -v "$(pwd)"/cache:/n4j/cache -v "$(pwd)"/results:/n4j/results sjones4/n4j:4.4 ./n4j.sh CLC_IP
```

This will output junit xml and html test results in the results directory.
