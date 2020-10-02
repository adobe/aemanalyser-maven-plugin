/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.mojos;

import org.apache.maven.plugin.MojoExecutionException;

import java.lang.reflect.Field;

public class MojoUtils {
    private MojoUtils() {}

    static void setParameter(Object mojo, String field, Object value)
            throws MojoExecutionException {
        setParameter(mojo, mojo.getClass(), field, value);
    }

    static void setParameter(Object mojo, Class<?> cls, String field, Object value)
            throws MojoExecutionException {
        try {
            try {
                Field f = cls.getDeclaredField(field);

                f.setAccessible(true);
                f.set(mojo, value);
            } catch (NoSuchFieldException e) {
                Class<?> sc = cls.getSuperclass();
                if (!sc.equals(Object.class)) {
                    // Try the superclass
                    setParameter(mojo, sc, field, value);
                } else {
                    throw e;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new MojoExecutionException("Problem configuring mojo: " + mojo.getClass().getName(), e);
        }
    }
}
