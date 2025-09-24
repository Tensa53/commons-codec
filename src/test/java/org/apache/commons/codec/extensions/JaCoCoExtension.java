package org.apache.commons.codec.extensions;

import org.jacoco.core.analysis.*;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.io.*;
import java.util.*;

import static org.apache.commons.codec.extensions.JaCoCoCoverageMatrix.updateCoverageMatrix;

public class JaCoCoExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        System.out.println("Started: " + getTestRun(context.getDisplayName())
                + context.getRequiredTestMethod().getName());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        System.out.println("Finished: " + getTestRun(context.getDisplayName())
                + context.getRequiredTestMethod().getName());
        updateCoverageMatrix(context.getRequiredTestMethod().getName(), context.getRequiredTestClass().getName());
    }

    private String getTestRun(String displayName) {
        int i1 = displayName.indexOf("[");
        int i2 = displayName.lastIndexOf("]");

        if (i1 != -1 && i2 != -1) {
            return displayName.substring(i1, i2 + 1) + " ";
        } else {
            return "";
        }
    }

}
