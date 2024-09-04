/*
  Copyright 2024 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;
import org.apache.sling.feature.scanner.BundleDescriptor;
import org.apache.sling.feature.scanner.PackageInfo;

/**
 * This task lists all package imports which are *not* resolvable with the current feature.
 * If this task is run on the user feature, it will list all package imports which are
 * resolved by the product feature. This task does not check for the actual presence of
 * the packages in the product feature. That is done by the usual feature analyser tasks.
 */
public class ProductPackageImportTask implements AnalyserTask {

    @Override
    public String getName() {
        return "Product Package Import";
    }

    @Override
    public String getId() {
        return "product-package-import";
    }

    /**
     * Get the list of bundles exporting packages
     * @param ctx The analyser task context
     * @return The list of bundles exporting packages
     */
    private List<BundleDescriptor> getExportingBundles(final AnalyserTaskContext ctx) {
        final List<BundleDescriptor> exportingBundles = new ArrayList<>();
        if ( ctx.getFrameworkDescriptor() != null ) {
            exportingBundles.add(ctx.getFrameworkDescriptor());
        }
        for(final BundleDescriptor bd : ctx.getFeatureDescriptor().getBundleDescriptors()) {
            if ( !bd.getExportedPackages().isEmpty() ) {
                exportingBundles.add(bd);
            }
        }
        return exportingBundles;
    }

    @Override
    public void execute(final AnalyserTaskContext ctx) throws IOException {
        final List<BundleDescriptor> exportingBundles = this.getExportingBundles(ctx);

        final Set<String> missingExports = new TreeSet<>();
        for(final BundleDescriptor info : ctx.getFeatureDescriptor().getBundleDescriptors()) {
            for(final PackageInfo pck : info.getImportedPackages() ) {
                final List<BundleDescriptor> candidates = getCandidates(exportingBundles, pck);
                if ( candidates.isEmpty() ) {
                    missingExports.add(pck.getName());
                }
            }
        }
        if (!missingExports.isEmpty()) {
            final int batchSize = 50;
            final List<Set<String>> batches = new ArrayList<>();
            Set<String> currentBatch = null;
            for (String missingExport : missingExports) {
                if (currentBatch == null) {
                    currentBatch = new TreeSet<>();
                    batches.add(currentBatch);
                }
                currentBatch.add(missingExport);
                if (currentBatch.size() >= batchSize) {
                    currentBatch = null;
                }
            }

            for (final Set<String> batch : batches) {
                ctx.reportWarning("The following packages are imported from the product: " + batch);
            }
        }
    }

    private List<BundleDescriptor> getCandidates(final List<BundleDescriptor> exportingBundles, final PackageInfo pck) {
        final List<BundleDescriptor> candidates = new ArrayList<>();
        for(final BundleDescriptor info : exportingBundles) {
            if ( info.isExportingPackage(pck) ) {
                candidates.add(info);
            }
        }
        return candidates;
    }
}
