/**
 * Copyright (C) 2012 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.configuration_doclet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.RootDoc;

public class ConfigurationDoclet {

  private static final String ARG_OUTPUT_DIRECTORY = "-d";

  private static final String ARG_ANNOTATION_TYPE = "-annotationType";

  public static boolean start(RootDoc root) {
    try {
      ConfigurationDoclet doclet = new ConfigurationDoclet();
      doclet.parseOptions(root.options());
      doclet.processClasses(root);
      return true;
    } catch (IOException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  public static int optionLength(String option) {
    if (option.equals(ARG_OUTPUT_DIRECTORY)) {
      return 2;
    } else if (option.equals(ARG_ANNOTATION_TYPE)) {
      return 2;
    }
    return 0;
  }

  public static boolean validOptions(String allOptions[][],
      DocErrorReporter reporter) {
    boolean foundOutputDirectory = false;
    boolean foundAnnotationType = false;
    for (String[] options : allOptions) {
      if (options.length == 0) {
        continue;
      }
      if (options[0].equals(ARG_OUTPUT_DIRECTORY)) {
        foundOutputDirectory = true;
      } else if (options[0].equals(ARG_ANNOTATION_TYPE)) {
        foundAnnotationType = true;
      }
    }
    if (!foundOutputDirectory) {
      reporter.printError("Usage: javadoc -d outputDirectory ...");
      return false;
    }
    if (!foundAnnotationType) {
      reporter.printError("Usage: javadoc -annotationType annotation.Type ...");
      return false;
    }
    return true;
  }

  private File _outputDirectory;

  private String _annotationType;

  private void parseOptions(String[][] allOptions) {
    for (String[] options : allOptions) {
      if (options.length == 0) {
        continue;
      }
      if (options[0].equals(ARG_OUTPUT_DIRECTORY)) {
        _outputDirectory = new File(options[1]);
      } else if (options[0].equals(ARG_ANNOTATION_TYPE)) {
        _annotationType = options[1];
      }
    }
  }

  private void processClasses(RootDoc root) throws IOException {
    PrintWriter writer = new PrintWriter(new FileWriter(new File(
        _outputDirectory, "index.xml")));
    writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    writer.println("<beans xmlns=\"http://www.springframework.org/schema/beans\"");
    writer.println("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
    writer.println("  xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd\">");
    writer.println("");
    writer.println("  <bean class=\"org.onebusaway.container.spring.PropertyOverrideConfigurer\">");
    writer.println("    <property name=\"properties\">");
    writer.println("      <props>");

    // <prop key="defaultWebappConfigurationSource.googleMapsApiKey">what</prop>

    ClassDoc[] classes = root.classes();
    for (int i = 0; i < classes.length; ++i) {
      for (MethodDoc method : classes[i].methods()) {
        if (hasMatchingAnnotation(method)) {

          System.out.println(classes[i] + "#" + method.name());
          System.out.println("commentText=" + method.commentText());

          String beanName = getBeanNameForClass(classes[i]);
          String propertyName = getPropertyName(method);
          List<String> values = new ArrayList<String>();
          add(method.commentText().trim(), values);
          add(getParamText(method, propertyName), values);
          List<String> commentText = values;

          writer.println("        <!--");
          for (String comment : commentText) {
            writer.println("          " + comment);
          }
          writer.println("        -->");
          writer.println("        <prop key=\"" + beanName + "." + propertyName
              + "\">...</prop>");
          writer.println();

          for (ParamTag tag : method.paramTags()) {
            System.out.println("  tag=" + tag.name() + " "
                + tag.parameterName() + " " + tag.parameterComment());

          }
        }
      }
    }

    writer.println("      </props>");
    writer.println("    </property>");
    writer.println("  </bean>");
    writer.println("</beans>");
    writer.close();
  }

  private boolean hasMatchingAnnotation(MethodDoc method) {
    for (AnnotationDesc annotation : method.annotations()) {
      try {
        AnnotationTypeDoc typeDoc = annotation.annotationType();
        if (_annotationType.equals(typeDoc.qualifiedName())) {
          return true;
        }
      } catch (ClassCastException e) {
        /**
         * HEY! If you are looking here because
         * "ClassDocImpl cannot be cast to AnnotationTypeDoc", the workaround is
         * adding your runtime jars to the classpath:
         * 
         * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6442982
         * 
         * but we are safe to assume that it was no TLDGen annotation, so we are
         * safe in returning false.
         */
        System.err.println("Detected ClassCastException (javadoc bug 6442982), ignoring");
      }
    }
    return false;
  }

  private String getBeanNameForClass(ClassDoc classDoc) {
    String name = classDoc.name();
    return beanify(name);
  }

  private String getPropertyName(MethodDoc method) {
    String name = method.name();
    if (name.startsWith("set")) {
      name = name.substring(3);
    }
    return beanify(name);
  }

  private String beanify(String name) {
    if (name.isEmpty()) {
      return "";
    }
    return name.substring(0, 1).toLowerCase() + name.substring(1);
  }

  private String getParamText(MethodDoc method, String propertyName) {
    ParamTag[] tags = method.paramTags();
    if (tags.length == 0) {
      return "";
    }
    ParamTag tag = tags[0];
    return propertyName + " - " + tag.parameterComment();
  }

  private void add(String value, List<String> values) {
    if (!values.isEmpty()) {
      values.add("");
    }
    for (String line : value.split("\n")) {
      values.add(line.trim());
    }
  }
}
