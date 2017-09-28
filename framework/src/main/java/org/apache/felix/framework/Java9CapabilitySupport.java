/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.framework;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

public class Java9CapabilitySupport {
    
    // Capability namespace for JPMS modules available in the platform
    private static final String JPMS_MODULE_NS = "jpms.module";

    private static final String PREFIX_JAVA_PACKAGE = "java.";

    // Build the system bundle exports and JPMS module capabilities for the
    // system bundle, based on the modules that are readable
    // to whatever module Felix resides in (usually the unnamed module).
    // Reflection is used to avoid building Felix on Java 9
    // and making it depend on Java 9+.
    public void buildPlatformModuleCapabilities(StringBuilder moduleCaps, StringBuilder packageCaps) {
        try {
            Class<?> clazz_Layer = Java9CapabilitySupport.class.getClassLoader().loadClass("java.lang.ModuleLayer");
            Method meth_class_getModule = Class.class.getMethod("getModule");

            // Module myModule = Java9CapabilitySupport.class.getModule();
            Object myModule = meth_class_getModule.invoke(Java9CapabilitySupport.class);
            Class<? extends Object> clazz_Module = myModule.getClass();
            Method meth_module_getLayer = clazz_Module.getMethod("getLayer");

            // ModuleLayer myLayer = myModule.getLayer();
            Object myLayer = meth_module_getLayer.invoke(myModule);
            if (myLayer == null) {
                // ModuleLayer bootLayer = ModuleLayer.boot();
                Object bootLayer = clazz_Layer.getMethod("boot").invoke(null);
                myLayer = bootLayer;
            }
            Method meth_layer_modules = clazz_Layer.getMethod("modules");
            Method meth_module_canRead = clazz_Module.getMethod("canRead", clazz_Module);
            Method meth_module_getName = clazz_Module.getMethod("getName");
            Method meth_module_getPackages = clazz_Module.getMethod("getPackages");
            Method meth_module_isExported = clazz_Module.getMethod("isExported", String.class, clazz_Module);

            // Set<Module> modules = myLayer.modules();
            @SuppressWarnings("unchecked")
            Collection<Object> modules = (Collection<Object>) meth_layer_modules.invoke(myLayer);
            SortedSet<String> visiblePackages = new TreeSet<String>();
            for (Object module : modules) {
                // myModule.canRead(module);
                boolean canRead = (Boolean) meth_module_canRead.invoke(myModule, module);
                if (canRead) {
                    moduleCaps.append(", ").append(JPMS_MODULE_NS).append("; ").append(JPMS_MODULE_NS).append("=")
                            .append(meth_module_getName.invoke(module));

                    // module.getPackages();
                    @SuppressWarnings("unchecked")
                    Collection<String> allPackages = (Collection<String>) meth_module_getPackages.invoke(module);
                    for (String packageName : allPackages) {
                        if (packageName.startsWith(PREFIX_JAVA_PACKAGE))
                            continue;
                        // module.isExported(packageName, myModule); // is this package exported to at least my module?
                        boolean isExported = (Boolean) meth_module_isExported.invoke(module, packageName, myModule);
                        if (isExported)
                            visiblePackages.add(packageName);
                    }
                }
            }

            // TODO: how to calculate and append the uses directive??
            for (String packageName : visiblePackages)
                packageCaps.append(",").append(packageName).append(";version=0.0.0.9_JavaSE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
