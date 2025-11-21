Automated Test Generation and Code Refactoring
-

Test Generation
--
At the moment, this project can generate simple unit tests for most methods and it can also generate API tests using RESTAssured for simple end points. 
The end goal is to be able to generate unit tests for full coverage and API tests for all end points in a restfull web application.

currently only maven projects are supported, but gradle projects will be added soon.

The expression evaluation engine is used in the test generation project, and it relies heavily on reflection. 
So don't forget to add the following VM argument
     `--add-opens java.base/java.util.stream=ALL-UNNAMED`

Code Refactoring
--
Once the parse tree for all classes have been built and the relationships between classes have been established through 
antikythera, it's possible to use that information to carry out varous refactoring tasks. Antikythera not only parses java
code but also HQL and SQL. A current use case is anlysis of JPA queries to check performance issues (and making changes as needed)

Overview of the project
--
The code is divided into several modules:

    1. Parser: This module is responsible for parsing the source code and extracting the necessary information to generate tests.
          does so by leveraging the JavaParser library. The heart of this module is the AbstractCompiler class. Both refactoring
          and test generation make use of this infrastructure.

    2. Expression evaluation engine: Evaluates each statement in the code keeping an eye out for branching and return statements.
            This feature is mostly used by the test generator (see below). 
            Whenever a branching statement is encountered, it will set up preconditions so that in the next evaluation run the 
            alternative branch will be executed. When a return statement is encountered, it will pass onto the test generation module.

    3. Test Generator: The test generation modules is centered around the TestGenerator class. Tests will be generated based on the
            return values encountered in the expression evaluation engine. When the return is not void, assertions will be generated
            based on the return value. For void methods, assertions will be based on logging statements and side effects. 

    4. Examples and Tools: The antikythera-examples module contains practical tools and utilities built on the core framework,
            including query optimization tools, code analysis utilities, and consolidated infrastructure components.

The test generator will also ensure that the proper preconditions are setup before the test is actually run.

Running the tests
--
Antikythera itself is tested by around 620 unit and integration tests. In order to be  execute them you need to clone the two repos at

- https://github.com/Cloud-Solutions-International/antikythera-sample-project
- https://github.com/Cloud-Solutions-International/antikythera-test-helper

If you maintain the folder structutre described in the antikythera-test-helper; chances are that you will not need to edit the yaml files in the src/test/resources
folder of this project. Running the tests will also require the --add-opens VM argument
