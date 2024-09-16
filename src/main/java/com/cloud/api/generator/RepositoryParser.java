package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepositoryParser extends ClassProcessor{

    public RepositoryParser() throws IOException {
        super();
    }

    public static void main(String[] args) throws IOException {
        Settings.loadConfigMap();
        RepositoryParser parser = new RepositoryParser();
        parser.process();
    }

    private void process() throws FileNotFoundException {
        File f = new File("/home/raditha/workspace/python/CSI/selenium/repos/EHR-IP/csi-ehr-ip-java-sev/src/main/java/com/csi/vidaplus/ehr/ip/admissionwithcareplane/dao/discharge/DischargeDetailRepository.java");
        CompilationUnit cu = javaParser.parse(f).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        cu.accept(new Visitor(), null);
    }

    class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            System.out.println("Method: " + n.getName());
            for(var ann : n.getAnnotations()) {
                if(ann.getNameAsString().equals("Query")) {
                    String query = null;
                    if (ann.isSingleMemberAnnotationExpr()) {
                        query = ann.asSingleMemberAnnotationExpr().getMemberValue().toString();
                    } else if (ann.isNormalAnnotationExpr()) {
                        boolean nt = false;
                        for(var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if(pair.getNameAsString().equals("nativeQuery")) {
                                if(pair.getValue().toString().equals("true")) {
                                    nt = true;
                                    System.out.println("\tNative Query");
                                }
                                else {
                                    System.out.println("\tJPQL Query");
                                }
                            }
                        }
                        for(var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if(pair.getNameAsString().equals("value")) {
                                query = pair.getValue().toString();
                            }
                        }
                    }

                    query = cleanUp(query);
                    try {
                        Statement bada = CCJSqlParserUtil.parse(cleanUp(query));
                        System.out.println(TablesNamesFinder.findTables(query));
                        System.out.println("\t" + bada);
                    } catch (JSQLParserException e) {
                        System.out.println("\tUnparsable: " + query);
                    }
                }
            }
        }

        private String cleanUp(String sql) {
            // If a JPA query is using a projection, we will have a new keyword immediately after the select
            // JSQL does not recognize this. So we will remove everything from the NEW keyword to the FROM
            // keyword and replace it with the '*' character.
            // Use case-insensitive regex to find and replace the NEW keyword and the FROM keyword
            Pattern pattern = Pattern.compile("new\\s+.*?\\s+from\\s+", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                sql = matcher.replaceAll(" * from ");
            }

            // Remove '+' signs only when they have spaces and a quotation mark on either side
            sql = sql.replaceAll("\"\\s*\\+\\s*\"", " ");

            // Remove quotation marks
            sql = sql.replace("\"", "");

            return sql;
        }
    }
}

