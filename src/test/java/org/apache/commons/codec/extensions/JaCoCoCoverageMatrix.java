package org.apache.commons.codec.extensions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

public class JaCoCoCoverageMatrix {

    public static void updateCoverageMatrix(String testMethodName, String testClassName) {
        try {
            // Connect to the platform MBean server
            MBeanServerConnection mbsc = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(JACOCO_MBEAN_NAME);

            // Invoke the dump command with no reset (you can set to true if you want to reset coverage after each dump)
            byte[] executionData = (byte[]) mbsc.invoke(objectName, "getExecutionData", new Object[]{true}, new String[]{"boolean"});

            // Use JaCoCo's ExecutionDataReader to parse the data
            ExecutionDataStore executionDataStore = new ExecutionDataStore();
            SessionInfoStore sessionInfoStore = new SessionInfoStore();
            ExecutionDataReader reader = new ExecutionDataReader(new ByteArrayInputStream(executionData));
            reader.setExecutionDataVisitor(executionDataStore);
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.read();

//            System.out.println("Covered source code methods for test case: " + context.getRequiredTestMethod().getName());

            // Analyze the covered classes to determine methods
            CoverageBuilder coverageBuilder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);

            // Specify the directory where your compiled classes are located
            File classesDir = new File("target/classes/"); // Adjust the path as needed

            ArrayList<String> fullyQualifiedCurrentMethods = new ArrayList<>();

            // Analyze each class file to extract covered methods
            for (ExecutionData data : executionDataStore.getContents()) {
                if (data.hasHits()) {
                    String className = data.getName().replace("/", ".");
//                    System.out.println("Class: " + className);

                    // Analyze the corresponding .class file
                    File classFile = new File(classesDir, data.getName() + ".class");
                    if (classFile.exists()) {
                        try (FileInputStream classStream = new FileInputStream(classFile)) {
                            analyzer.analyzeClass(classStream, data.getName());
                        }
                    }

                    // Print the covered method names
                    Set<String> coveredMethods = getCoveredMethods(coverageBuilder, className);
                    ArrayList<String> coveredMethodsFullyQualified = new ArrayList<>();
                    for (String method : coveredMethods) {
                        if(method.contains("<init>"))
                            method = method.replace("<init>", getSimpleClassName(className));
                        fullyQualifiedCurrentMethods.add(className+"."+method);
                        coveredMethodsFullyQualified.add(className+"."+method);
                    }

                    // Update the json coverage-matrix file
                    updateCoverageMatrixFile(testClassName+"." + testMethodName,coveredMethodsFullyQualified);
                }
            }

            deleteOlderCoveredMethodsFromMatrix(testClassName+"." + testMethodName,fullyQualifiedCurrentMethods);

        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getSimpleClassName(String className) {
        if (className.contains(".")) {
            return className.substring(className.lastIndexOf('.') + 1);
        }
        return className;
    }

    private static Set<String> getCoveredMethods(CoverageBuilder coverageBuilder, String className) {
        Set<String> coveredMethods = new HashSet<>();
        className = className.replace(".", "/");
        for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
            if (classCoverage.getName().equals(className)) {
                for (IMethodCoverage methodCoverage : classCoverage.getMethods()) {
                    if (methodCoverage.getInstructionCounter().getCoveredCount() > 0) {
                        StringBuilder methodName = new StringBuilder();

                        methodName.append(methodCoverage.getName()); // Get method name
                        String methodDescriptor = methodCoverage.getDesc();// Get method descriptor

                        String signature = getMethodSignature(methodDescriptor);

                        methodName.append("(").append(signature).append(")");

                        int firstLine = methodCoverage.getFirstLine();
                        int lastLine = methodCoverage.getLastLine();

                        StringBuilder coveredlines = new StringBuilder();
                        coveredlines.append("{");

                        for (int line = firstLine; line <= lastLine; line++) {
                            ILine lineCoverage =  methodCoverage.getLine(line);

                            if (lineCoverage.getInstructionCounter().getCoveredCount() > 0) {
//                                System.out.println("Covered line: " + line);
                                coveredlines.append("L").append(line).append(";");
                            } else {
//                                System.out.println("Uncovered line: " + line);
                            }
                        }

                        coveredlines.append("}");

                        methodName.append(coveredlines);

                        coveredMethods.add(methodName.toString());
                    }
                }
            }
        }
        return coveredMethods;
    }

    private static String getMethodSignature(String methodDescriptor) {

        String parameters = methodDescriptor.substring(methodDescriptor.indexOf("(") + 1, methodDescriptor.indexOf(")"));

        String array = "[]";

        int i = 0;

        int len = parameters.length();

        StringBuilder signature = new StringBuilder();

        while (i < len) {
            String typeName;
            //squares to know the new position for substring: 1 in case of array, 2 in case of matrix
            int squares = 0;
            //skip to know the new position for substring: 2 in case of class instance ("type letter" and semicolon)
            int skip = 2;
            char character = parameters.charAt(i);

            if (character == '[') {
                if (parameters.charAt(i + 1) == '[') {
                    squares = 2;
                    typeName = getTypeNamebyDescriptor(parameters.substring(i + squares));
                    signature.append(typeName).append(array).append(array).append(",");
                    if (parameters.charAt(i + squares) == 'L') {
                        //in case of a class, other than the letter, the descriptor tells also the name of the class
                        i += squares + skip + typeName.length();
                    } else {
                        i += squares + 1;
                    }
                } else {
                    squares = 1;
                    typeName = getTypeNamebyDescriptor(parameters.substring(i + 1));
                    signature.append(typeName).append(array).append(",");
                    if (parameters.charAt(i + squares) == 'L') {
                        i += squares + skip + typeName.length();
                    } else {
                        i += squares + 1;
                    }
                }
            } else {
                typeName = getTypeNamebyDescriptor(parameters.substring(i));
                signature.append(typeName).append(",");
                if (parameters.charAt(i) == 'L') {
                    i += skip + typeName.length();
                } else {
                    i += 1;
                }
            }
        }

        int lastColumn = signature.lastIndexOf(",");

        if (lastColumn != -1) {
            String sig = signature.substring(0,lastColumn);
//            System.out.println("Signature:" + sig);
            return sig;
        } else {
//            System.out.println("Signature:" + signature.toString());

            return signature.toString();
        }

    }

    private static String getTypeNamebyDescriptor(String desc) {
        String name = "";

        switch (desc.charAt(0)) {
            case 'B':
                name = "byte";
                break;
            case 'C':
                name = "char";
                break;
            case 'D':
                name = "double";
                break;
            case 'F':
                name = "float";
                break;
            case 'I':
                name = "int";
                break;
            case 'J':
                name = "long";
                break;
            case 'S':
                name = "short";
                break;
            case 'Z':
                name = "boolean";
                break;
            case 'L':
                String s = desc.substring(1,desc.indexOf(";"));
                name = s.replace("/",".");
        }

        return name;
    }

    private static void updateCoverageMatrixFile(String testName, ArrayList<String> coveredMethods) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Set<String>> coverageMatrix = new HashMap<>();

        // Read existing coverage-matrix.json if it exists
        File coverageFile = new File(COVERAGE_MATRIX_FILE);
        if (coverageFile.exists()) {
            try {
                coverageMatrix = objectMapper.readValue(coverageFile, new TypeReference<Map<String, Set<String>>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to read coverage-matrix.json");
            }
        }

        // Update the coverage matrix
        coverageMatrix.computeIfAbsent(testName, k -> new HashSet<>());

        for (String method : coveredMethods) {
            coverageMatrix.get(testName).add(method);
        }

        // Write the updated coverage matrix back to the file, creating the file if it doesn't exist
        try {
            if (!coverageFile.exists()) {
                coverageFile.createNewFile();
            }
            try (FileWriter fileWriter = new FileWriter(coverageFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileWriter, coverageMatrix);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write coverage-matrix.json");
        }
    }

    private static void deleteOlderCoveredMethodsFromMatrix(String testName, ArrayList<String> fullyQualifiedCurrentMethods) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Set<String>> coverageMatrix = new HashMap<>();

        // Read existing coverage-matrix.json if it exists
        File coverageFile = new File(COVERAGE_MATRIX_FILE);
        if (coverageFile.exists()) {
            try {
                coverageMatrix = objectMapper.readValue(coverageFile, new TypeReference<Map<String, Set<String>>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to read coverage-matrix.json");
            }
        }

        // Update the coverage matrix
        Set<String> existingMethods = coverageMatrix.computeIfAbsent(testName, k -> new HashSet<>());

        Set<String> methodsToRemove = new HashSet<>(existingMethods);
        for (String method : methodsToRemove) {
            if (!fullyQualifiedCurrentMethods.contains(method)) {
                existingMethods.remove(method);
            }
        }

        // Write the updated coverage matrix back to the file, creating the file if it doesn't exist
        try {
            if (!coverageFile.exists()) {
                coverageFile.createNewFile();
            }
            try (FileWriter fileWriter = new FileWriter(coverageFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileWriter, coverageMatrix);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to write coverage-matrix.json");
        }
    }

    private static final String JACOCO_MBEAN_NAME = "org.jacoco:type=Runtime";
    private static final String COVERAGE_MATRIX_FILE = "coverage-matrix.json";
}
