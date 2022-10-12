/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.tasks;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

import javax.jcr.PropertyType;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.felix.cm.file.ConfigurationHandler;
import org.apache.felix.cm.json.Configurations;
import org.apache.jackrabbit.util.ISO8601;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A configuration file
 */
public final class ConfigurationFile {

    public enum Location {
        APPS,
        LIBS;
    }

    private String runMode;
    private int level = -1;
    private final File source;
    private final ConfigurationFileType type;
    private final Location location;

    public ConfigurationFile(final Location l, final File source) {
        this(l, source, ConfigurationFileType.fromFileName(source.getName()));
    }

    public ConfigurationFile(final Location l, final File source, final ConfigurationFileType type) {
        this.location = l;
        this.source = source;
        this.type = type;
    }

    /**
     * @return the location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * @return the source
     */
    public File getSource() {
        return source;
    }

    /**
     * @return the type
     */
    public ConfigurationFileType getType() {
        return type;
    }

    /**
     * @return the runMode
     */
    public String getRunMode() {
        return runMode;
    }

    /**
     * @param runMode the runMode to set
     */
    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    /**
     * @return the level
     */
    public int getLevel() {
        return level;
    }

    /**
     * @param level the level to set
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Get the pid
     * @return The pid
     */
    public String getPid() {
        int pos = source.getName().lastIndexOf(".");
        if ( type == ConfigurationFileType.JSON ) {
            pos = pos - 4;
        }
        final String id = source.getName().substring(0, pos);
        pos = id.indexOf('-');
        if (pos != -1) {
            return id.substring(0, pos).concat("~").concat(id.substring(pos + 1));
        }
        return id;
    }

    public Dictionary<String, Object> readConfiguration() throws IOException {
        try ( final InputStream input = new FileInputStream(this.getSource()) ) {
            if ( this.getType() == ConfigurationFileType.CONFIGADMIN ) {
                return ConfigurationHandler.read(input);
            } else if ( this.getType() == ConfigurationFileType.JSON ) {
                return Configurations.buildReader()
                    .withIdentifier(this.getSource().getAbsolutePath())
                    .build(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .readConfiguration();    
            } else if ( this.getType() == ConfigurationFileType.PROPERTIES ) {
                final Properties properties = new Properties();

                try (final BufferedInputStream in = new BufferedInputStream(input)) {
                    in.mark(1);
        
                    boolean isXml = '<' == in.read();
        
                    in.reset();
        
                    if (isXml) {
                        properties.loadFromXML(in);
                    } else {
                        properties.load(in);
                    }
                }
        
                Dictionary<String, Object> configuration = new Hashtable<>();
                final Enumeration<Object> i = properties.keys();
                while (i.hasMoreElements()) {
                    final Object key = i.nextElement();
                    configuration.put(key.toString(), properties.get(key));
                }
        
                return configuration;
            } else if ( this.getType() == ConfigurationFileType.XML ) {
                return new JcrConfigurationParser().parse(input);
            }
        }
        throw new IOException("Unknown format for configuration file " + this.getSource());
    }

    private static final class JcrConfigurationParser extends DefaultHandler {

        private static final String SLING_OSGICONFIG = "sling:OsgiConfig";

        private static final String JCR_ROOT = "jcr:root";

        private static final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    
        private Dictionary<String, Object> configuration;

        public Dictionary<String, Object> parse(final InputStream input) throws IOException {
            try {
                final SAXParser saxParser = saxParserFactory.newSAXParser();
                saxParser.parse(input, this);
                return this.configuration;    
            } catch ( final ParserConfigurationException | SAXException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
            if (JCR_ROOT.equals(qName)) {
                final String primaryType = attributes.getValue(JCR_PRIMARYTYPE);
                if (SLING_OSGICONFIG.equals(primaryType)) {
                    parseOSGiConfiguration(uri, localName, qName, attributes);
                }
            }
        }
        
        private void parseOSGiConfiguration(String uri, String localName, String qName, Attributes attributes) {
            this.configuration = Configurations.newConfiguration();

            for (int i = 0; i < attributes.getLength(); i++) {
                final String attributeQName = attributes.getQName(i);

                // ignore jcr: and similar properties
                if (attributeQName.indexOf(':') == -1) {
                    final String attributeValue = attributes.getValue(i);
                    if (attributeValue != null && !attributeValue.isEmpty() ) {
                        final DocViewProperty property = DocViewProperty.parse(attributeQName, attributeValue);
                        final Object[] values = getValues(property);
                        if (values.length == 0) {
                            // ignore empty values (either property.values were empty or value mapping resulted in null 
                            // results that got filtered)
                            continue;
                        }
                        if (!property.isMulti) {
                            // first element to be used in case of single-value property
                            configuration.put(attributeQName, values[0]);
                        } else {
                            configuration.put(attributeQName, values);
                        }
                    }
                }
            }
        }
        
        private Object[] getValues(final DocViewProperty property) {
            Object[] values;
            switch (property.type) {
                case PropertyType.DATE:
                    // Date was never properly supported as osgi configs don't support dates so converting to millis 
                    // Scenario should just be theoretical
                    values = mapValues(property.values, s -> {
                        Calendar cal = ISO8601.parse(s);
                        return (cal != null) ? cal.getTimeInMillis() : null;
                    });
                    break;
                case PropertyType.DOUBLE:
                    values = mapValues(property.values, Double::parseDouble);
                    break;
                case PropertyType.LONG:
                    values = mapValues(property.values, Long::parseLong);
                    break;
                case PropertyType.BOOLEAN:
                    values = mapValues(property.values, Boolean::valueOf);
                    break;
                default:
                    values = property.values;
            }
            return values;
        }
        
        private static Object[] mapValues(final String[] strValues, final Function<String, Object> function) {
            return Arrays.stream(strValues).filter(s -> s != null && !s.isEmpty()).map(function).filter(Objects::nonNull).toArray();
        }
    }
}