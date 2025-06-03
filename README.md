Automated Test Generator for java projects 
-

At the moment this project can generate simple unit tests for most methods and it can also generate API tests using RESTAssured for simple end points. 
The end goal is to be able to generate unit tests for full coverage and API tests for all end points in a restfull web application.

At the moment only maven projects are supported but gradle projects will be added soon.

The expression evaluation engine is used in the test generation project and it relies heavily on reflection. So don't forget to add the following VM argument
     `--add-opens java.base/java.util.stream=ALL-UNNAMED`

Running the tests
--
Antikythera itself is tested by around 450 unit and integration tests. In order for you to be able to execute them you need to clone the two repos at

 - https://github.com/Cloud-Solutions-International/antikythera-sample-project
 - https://github.com/Cloud-Solutions-International/antikythera-test-helper

If you maintain the folder structutre described in the antikythera-test-helper; chances are that you will not need to edit the yaml files in the src/test/resources 
folder of this project. Running the tests will also require the --add-opens VM argument
