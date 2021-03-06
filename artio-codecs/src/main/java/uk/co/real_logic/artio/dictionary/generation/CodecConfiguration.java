/*
 * Copyright 2020 Adaptive Financial Consulting Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.dictionary.generation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class CodecConfiguration
{
    /**
     * Boolean system property to turn on or off duplicated fields validation. Defaults to false.
     * <p>
     * Fix specification vol 1:
     * A tag number (field) should only appear in a message once. If it appears more than once in the message it should
     * be considered an error with the specification document.
     * <p>
     * Turning this option on may break parsing: this option should be used for support fix specification with error
     * only. It is recommended, where possible, to correct your FIX XML file instead of using this option in order
     * to support an invalid XML file.
     * <br>
     * The duplicated fields is allowed in the following case:
     * <pre>
     * message body:
     * field;
     * repeating group:
     * the_other_field+
     * field;
     * </pre>
     */
    public static final String FIX_CODECS_ALLOW_DUPLICATE_FIELDS_PROPERTY = "fix.codecs.allow_duplicate_fields";
    public static final String PARENT_PACKAGE_PROPERTY = "fix.codecs.parent_package";
    public static final String FLYWEIGHTS_ENABLED_PROPERTY = "fix.codecs.flyweight";
    public static final String REJECT_UNKNOWN_ENUM_VALUE_PROPERTY = "reject.unknown.enum.value";

    public static final String DEFAULT_PARENT_PACKAGE = "uk.co.real_logic.artio";

    private String parentPackage = System.getProperty(PARENT_PACKAGE_PROPERTY, DEFAULT_PARENT_PACKAGE);
    private boolean flyweightsEnabled = Boolean.getBoolean(FLYWEIGHTS_ENABLED_PROPERTY);
    private boolean allowDuplicateFields = Boolean.getBoolean(FIX_CODECS_ALLOW_DUPLICATE_FIELDS_PROPERTY);

    private String codecRejectUnknownEnumValueEnabled;
    private String outputPath;
    private String[] fileNames;
    private InputStream[] fileStreams;

    public CodecConfiguration()
    {
    }

    public CodecConfiguration outputPath(final String outputPath)
    {
        this.outputPath = outputPath;
        return this;
    }

    public CodecConfiguration parentPackage(final String parentPackage)
    {
        this.parentPackage = parentPackage;
        return this;
    }

    public CodecConfiguration flyweightsEnabled(final boolean flyweightsEnabled)
    {
        this.flyweightsEnabled = flyweightsEnabled;
        return this;
    }

    public CodecConfiguration allowDuplicateFields(final boolean allowDuplicateFields)
    {
        this.allowDuplicateFields = allowDuplicateFields;
        return this;
    }

    public CodecConfiguration fileNames(final String... fileNames)
    {
        this.fileNames = fileNames;
        return this;
    }

    public CodecConfiguration fileStreams(final InputStream... fileStreams)
    {
        this.fileStreams = fileStreams;
        return this;
    }

    /**
     * String representing a Java expressions that evaluates to a boolean within the codec that states whether
     * an unknown enum value within a codec should be rejected or not. Evaluation to true rejects. This could be
     * a constant value or a reference to some other Java code that evaluates to a boolean.
     *
     * For example <code>"true"</code> or <code>"ExternalConfigClass.REJECT_UNKNOWN_ENUM"</code>.
     *
     * @param codecRejectUnknownEnumValueEnabled the String
     * @return this
     */
    public CodecConfiguration codecRejectUnknownEnumValueEnabled(final String codecRejectUnknownEnumValueEnabled)
    {
        this.codecRejectUnknownEnumValueEnabled = codecRejectUnknownEnumValueEnabled;
        return this;
    }

    public InputStream[] fileStreams()
    {
        return fileStreams;
    }

    public String outputPath()
    {
        return outputPath;
    }

    public String parentPackage()
    {
        return parentPackage;
    }

    public boolean flyweightsEnabled()
    {
        return flyweightsEnabled;
    }

    public boolean allowDuplicateFields()
    {
        return allowDuplicateFields;
    }

    public String codecRejectUnknownEnumValueEnabled()
    {
        return codecRejectUnknownEnumValueEnabled;
    }

    void conclude() throws FileNotFoundException
    {
        if (outputPath() == null)
        {
            throw new IllegalArgumentException("Missing outputPath() configuration property");
        }

        if (codecRejectUnknownEnumValueEnabled == null)
        {
            final String rejectUnknownEnumPropertyValue = System.getProperty(REJECT_UNKNOWN_ENUM_VALUE_PROPERTY);
            codecRejectUnknownEnumValueEnabled = rejectUnknownEnumPropertyValue != null ?
                rejectUnknownEnumPropertyValue : Generator.RUNTIME_REJECT_UNKNOWN_ENUM_VALUE_PROPERTY;
        }

        // Create input streams from names if not provided.
        if (fileStreams == null)
        {
            if (fileNames == null)
            {
                throw new IllegalArgumentException(
                    "You must provide either the fileNames or fileStream configuration options");
            }

            final int n = fileNames.length;
            fileStreams = new InputStream[n];
            for (int i = 0; i < n; i++)
            {
                final File xmlFile = new File(fileNames[i]);
                if (!xmlFile.exists())
                {
                    throw new IllegalArgumentException("xmlFile does not exist: " + xmlFile.getAbsolutePath());
                }

                if (!xmlFile.isFile())
                {
                    throw new IllegalArgumentException(String.format(
                        "xmlFile [%s] isn't a file, are the arguments the correct way around?",
                        xmlFile));
                }

                fileStreams[i] = new FileInputStream(xmlFile);
            }
        }
    }
}
