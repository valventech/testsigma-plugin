package com.testsigma.plugins.util;

import hudson.model.Build;
import hudson.model.BuildListener;

import java.util.ArrayList;
import java.util.List;

public class CommonUtil {

    public static String extractTestPlanId(String testPlanId, Build<?, ?> build, BuildListener listener)  {
            String resolvedTestPlanId = resolveTestPlanId(testPlanId.trim(),build,listener);
            if(resolvedTestPlanId == null){
                listener.error(String.format("Given build parameter %s cannot be resolved.Available build parameters:%s",testPlanId,build.getBuildVariables()));
            }
        return resolvedTestPlanId;
    }

    private static String resolveTestPlanId(String idString,Build<?, ?> build,BuildListener listener) {
        boolean isNumber = isNumber(idString);
        if(isNumber){
            return idString;
        }
        //Handle build parameter
        String buildParamValue = build.getBuildVariables().get(idString);
        return buildParamValue;
    }

    private static boolean isNumber(String idString) {
        try{
            Integer.parseInt(idString);
            return true;
        }catch(Exception e){
        }
        return false;
    }
}
