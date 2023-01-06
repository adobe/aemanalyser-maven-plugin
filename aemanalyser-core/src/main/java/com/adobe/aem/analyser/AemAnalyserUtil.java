/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import com.adobe.aem.project.EnvironmentType;
import com.adobe.aem.project.RunModes;
import com.adobe.aem.project.ServiceType;

public class AemAnalyserUtil {

    /** Used run modes */
    static final Set<String> ALL_USED_MODES = Stream.concat(
        RunModes.AUTHOR_ONLY_MODES.stream(), RunModes.PUBLISH_ONLY_MODES.stream()).collect(Collectors.toSet());

    /** Default runmode */
    private static final String DEFAULT_MODE = "(default)";

    static Set<String> getUsedModes(EnumSet<ServiceType> st) {
        if (st.size() == 0) {
            throw new IllegalStateException("No service type specified");
        }

        if (st.size() == 1) {
            switch(st.iterator().next()) {
                case AUTHOR: return RunModes.AUTHOR_ONLY_MODES;
                case PUBLISH: return RunModes.PUBLISH_ONLY_MODES;
            }
        }

        return ALL_USED_MODES;
    }

    /**
     * Check if a runmode is invalid
     * @return {@code null} if is valid, the correct runmode if invalid
     * @deprecated Use {@link RunModes#checkIfRunModeIsSpecifiedInWrongOrder(String)}
     */
    static String getValidRunMode(final String mode) {
        return RunModes.checkIfRunModeIsSpecifiedInWrongOrder(mode);
    }

    /**
     * Check if run mode is used
     * @param mode The runmode
     * @param serviceTypes The service types to consider
     * @return {@code true} if mode is used
     * @deprecated Use {@link RunModes#matchesRunMode(ServiceType, String)} - which has a different semantics!
     */
    public static boolean isRunModeUsed(final String mode, EnumSet <ServiceType> serviceTypes) {
        return getUsedModes(serviceTypes).contains(mode);
    }

    /**
     * Calculate the aggregates based on the runmode mappings
     * @param runmodeProps The runmode mappings
     * @param serviceTypes The service types to calculate the aggregates for.
     * @return The aggregates
     * @throws IllegalArgumentException If an invalid runmode is used
     */
    public static Map<String, List<String>> getAggregates(Map<String, String> runmodeProps,
            EnumSet<ServiceType> serviceTypes) {
        return getAggregates(runmodeProps, serviceTypes, Collections.emptyMap());
    }

    /**
     * Calculate the aggregates based on the runmode mappings
     * @param runmodeProps The runmode mappings
     * @param serviceTypes The service types to calculate the aggregates for.
     * @param additionalRunmodes Extra runmodes to handle. The keys of the map have the new ones,
     * the values on the map has an existing runmode which is used as the bases for that new one.
     * @return The aggregates
     * @throws IllegalArgumentException If an invalid runmode is used
     */
    public static Map<String, List<String>> getAggregates(Map<String, String> runmodeProps,
            EnumSet<ServiceType> serviceTypes, Map<String, String> additionalRunmodes) {
        HashMap<String, String> rmp = new HashMap<>(runmodeProps);

        // Obtain the results map filled for each entry with what is provided for the
        // '(default)' runmode.
        Map<String, List<String>> result = primeResultWithDefault(rmp, serviceTypes);

        // Look for all modes found in the runmodeProps input, properly sorted
        for (String mode : sortPropertyNames(rmp.keySet())) {
            List<String> models = result.get(mode);
            boolean valid = false;
            if (models != null) {
                String[] sources = rmp.get(mode).split(",");
                for (String pck : sources) {
                    models.add(pck);
                }
                // Forward fill this in env-specific models too
                // e.g. author is also put in author.dev etc
                for (EnvironmentType et : EnvironmentType.values()) {
                    String key = mode + "." + et.asString();
                    List<String> subModels = result.get(key);
                    if (subModels != null) {
                        for (String pck : sources) {
                            subModels.add(pck);
                        }
                    }
                }
                valid = true;
            } else {
                for (ServiceType sp : ServiceType.values()) {
                    String key = sp.asString() + "." + mode;
                    models = result.get(key);
                    if (models != null) {
                        for (String pck : rmp.get(mode).split(",")) {
                            models.add(pck);
                        }
                        valid = true;
                    }
                }
            }

            if (valid) {
                rmp.remove(mode);
            }
        }

        handleAdditionalRunmodes(rmp, serviceTypes, additionalRunmodes, result);
        reportUnhandledModes(rmp);
        pruneModels(result, additionalRunmodes.keySet());

        return result;
    }

    private static Map<String, List<String>> primeResultWithDefault(Map<String, String> runmodeProps, EnumSet<ServiceType> serviceTypes) {
        Map<String, List<String>> result = new HashMap<>();

        for (String mode : getUsedModes(serviceTypes)) {
            result.put(mode, new ArrayList<>());
        }

        String defaultFm = runmodeProps.remove(DEFAULT_MODE);
        if (defaultFm != null ) {
            for(final String pck : defaultFm.toString().split(",")) {
                result.values().stream().forEach(s -> s.add(pck));
            }
        }

        return result;
    }

    /**
     * Handle additional runmodes which have been requested, these are over and above the default
     * runmodes. Additional runmodes are based on existing runmodes and provided for all requested
     * service types. <p>
     *
     * For example an additional runmode of 'rde' could be based on the existing runmode of 'dev'.
     * This will create 'author.rde' and 'publish.rde' runmodes (if both servicetypes are requested)
     * These runmodes will be based on 'author.dev' and 'publish.dev' which were previously created. <p>
     *
     * Then rde/author.rde/publish.rde are looked up in runmodeProps and added on top of the base.
     * @param runmodeProps
     * @param serviceTypes
     * @param additionalRunmodes
     * @param result
     */
    private static void handleAdditionalRunmodes(Map<String, String> runmodeProps,
            EnumSet<ServiceType> serviceTypes, Map<String, String> additionalRunmodes,
            Map<String, List<String>> result) {
        for (Map.Entry<String, String> addEntry : additionalRunmodes.entrySet()) {
            String newMode = addEntry.getKey();
            String baseMode = addEntry.getValue();
            // Prepopulate the result with additional entries
            for (ServiceType st : serviceTypes) {
                String bmKey = st.asString() + "." + baseMode;
                List<String> baseline = result.get(bmKey);
                if (baseline == null) {
                    throw new IllegalArgumentException("Cannot base runmode " + newMode + " on " + baseMode +
                        ". Baseline not found in runmodes: " + baseline);
                }
                result.put(st.asString() + "." + newMode, baseline);
            }

            // Forward-fill the general new runmode to all service type variants
            String newModeValues = runmodeProps.remove(newMode);
            if (newModeValues != null) {
                for (String pck : newModeValues.split(",")) {
                    for (ServiceType st : serviceTypes) {
                        result.get(st.asString() + "." + newMode).add(pck);
                    }
                }
            }

            // Now add specific ones too
            for (ServiceType st : serviceTypes) {
                String rmp = st.asString() + "." + newMode;
                String newModeSpecific = runmodeProps.remove(rmp);
                if (newModeSpecific != null) {
                    for (String pck : newModeSpecific.split(",")) {
                        result.get(rmp).add(pck);
                    }
                }
            }
        }
    }

    /**
     * This method reports on unhandled modes. If the mode is incorrectly specified it throws an exception.
     * @param rmp The remaining runmodes that weren't handled.
     * @throws IllegalArgumentException if the runmode is incorrectly formatted
     */
    private static void reportUnhandledModes(HashMap<String, String> rmp) {
        for (String mode : rmp.keySet()) {
            String validMode = getValidRunMode(mode);
            if ( validMode != null ) {
                throw new IllegalArgumentException("Invalid runmode " + mode + ". Please use this runmode instead: " + validMode);
            }
            LoggerFactory.getLogger(AemAnalyser.class.getName()).info("Ignoring unused runmode " + mode);
        }
    }

    /**
     * Sort the property names from least specific to most specific.
     * The specificness is indentified by the nubmer of dots. E.g.
     * author is less specific than author.dev. Also environments need
     * to come before service types. So stage comed before author.
     * @param stringPropertyNames the property names
     * @return the same names but now sorted
     */
    private static List<String> sortPropertyNames(Set<String> stringPropertyNames) {
        List<String> names = new ArrayList<>(stringPropertyNames);

        // Turn 'author' and 'publish' into uppercase so that they are sorted after
        // dev, stage and prod.
        for (int i=0; i<names.size(); i++) {
            String n = names.get(i);
            String n2 = n.replace("author", "AUTHOR");
            String n3 = n2.replace("publish", "PUBLISH");
            names.set(i, n3);
        }

        // Sort such that names with less dots (which are separators) come before more dots
        // If the number of dots are equals, sort alphabetically, which, given the capitalization
        // of the runmodes will put the environments before the runmodes.
        Collections.sort(names, (s1, s2) -> {
            int res = Long.compare(countChars(s1), countChars(s2));

            // If the number of dots is the same we sort alphabetically
            return res != 0 ? res : s1.compareTo(s2);
        });

        // Before we return we convert everything back into lowercase as it was.
        return names.stream().map(String::toLowerCase).collect(Collectors.toList());
    }

    private static long countChars(String s) {
        return s.chars().filter(c -> c == '.').count();
    }

    static void pruneModels(final Map<String, List<String>> allModels, Set<String> additionalRunmodes) {
        Set<String> allEnvs = Arrays.stream(EnvironmentType.values())
            .map(EnvironmentType::asString)
            .collect(Collectors.toSet());
        allEnvs.addAll(additionalRunmodes);

        // Remove specialised models that don't add anything
        for (ServiceType ap : ServiceType.values()) {
            for (String env : allEnvs) {
                String mode = ap.asString().concat(".").concat(env);
                if (Objects.equals(allModels.get(ap.asString()), allModels.get(mode))) {
                    allModels.remove(mode);
                }
            }
        }

        // If specialised models exist for all environments, remove the generic model, as
        // a specialised model is then always used
        publish:
        for (ServiceType ap : ServiceType.values()) {
            for (String env : allEnvs) {
                String mode = ap.asString().concat(".").concat(env);
                if (!allModels.containsKey(mode)) {
                    continue publish;
                }
            }

            // Found specialised models for all, remove the generic one
            allModels.remove(ap.asString());
        }
    }
}
