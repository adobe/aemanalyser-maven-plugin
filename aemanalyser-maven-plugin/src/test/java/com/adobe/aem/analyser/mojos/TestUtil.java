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

import java.lang.reflect.Field;

class TestUtil {
    private TestUtil() {}

    static Object getField(Object obj, Class<?> cls, String name)
            throws NoSuchFieldException, IllegalAccessException {
        try {
            Field f = cls.getDeclaredField(name);

            f.setAccessible(true);
            return f.get(obj);
        } catch (NoSuchFieldException e) {
            Class<?> sc = cls.getSuperclass();
            if (!sc.equals(Object.class)) {
                return getField(obj, sc, name);
            }
            throw e;
        }
    }
}
