// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.remote.http;

import static org.openqa.selenium.remote.DriverCommand.ACCEPT_ALERT;
import static org.openqa.selenium.remote.DriverCommand.DISMISS_ALERT;
import static org.openqa.selenium.remote.DriverCommand.EXECUTE_ASYNC_SCRIPT;
import static org.openqa.selenium.remote.DriverCommand.EXECUTE_SCRIPT;
import static org.openqa.selenium.remote.DriverCommand.FIND_CHILD_ELEMENT;
import static org.openqa.selenium.remote.DriverCommand.FIND_CHILD_ELEMENTS;
import static org.openqa.selenium.remote.DriverCommand.FIND_ELEMENT;
import static org.openqa.selenium.remote.DriverCommand.FIND_ELEMENTS;
import static org.openqa.selenium.remote.DriverCommand.GET_ALERT_TEXT;
import static org.openqa.selenium.remote.DriverCommand.GET_CURRENT_WINDOW_HANDLE;
import static org.openqa.selenium.remote.DriverCommand.GET_CURRENT_WINDOW_POSITION;
import static org.openqa.selenium.remote.DriverCommand.GET_CURRENT_WINDOW_SIZE;
import static org.openqa.selenium.remote.DriverCommand.GET_ELEMENT_ATTRIBUTE;
import static org.openqa.selenium.remote.DriverCommand.GET_ELEMENT_LOCATION;
import static org.openqa.selenium.remote.DriverCommand.GET_ELEMENT_LOCATION_ONCE_SCROLLED_INTO_VIEW;
import static org.openqa.selenium.remote.DriverCommand.GET_ELEMENT_RECT;
import static org.openqa.selenium.remote.DriverCommand.GET_ELEMENT_SIZE;
import static org.openqa.selenium.remote.DriverCommand.GET_PAGE_SOURCE;
import static org.openqa.selenium.remote.DriverCommand.GET_WINDOW_HANDLES;
import static org.openqa.selenium.remote.DriverCommand.IS_ELEMENT_DISPLAYED;
import static org.openqa.selenium.remote.DriverCommand.MAXIMIZE_CURRENT_WINDOW;
import static org.openqa.selenium.remote.DriverCommand.SET_ALERT_VALUE;
import static org.openqa.selenium.remote.DriverCommand.SET_CURRENT_WINDOW_POSITION;
import static org.openqa.selenium.remote.DriverCommand.SET_CURRENT_WINDOW_SIZE;
import static org.openqa.selenium.remote.DriverCommand.SUBMIT_ELEMENT;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;

import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.internal.WebElementToJsonConverter;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * A command codec that adheres to the W3C's WebDriver wire protocol.
 *
 * @see <a href="https://w3.org/tr/webdriver">W3C WebDriver spec</a>
 */
public class W3CHttpCommandCodec extends AbstractHttpCommandCodec {

  public W3CHttpCommandCodec() {
    alias(GET_ELEMENT_ATTRIBUTE, EXECUTE_SCRIPT);
    alias(GET_ELEMENT_LOCATION, GET_ELEMENT_RECT);
    alias(GET_ELEMENT_LOCATION_ONCE_SCROLLED_INTO_VIEW, EXECUTE_SCRIPT);
    alias(GET_ELEMENT_SIZE, GET_ELEMENT_RECT);
    alias(IS_ELEMENT_DISPLAYED, EXECUTE_SCRIPT);
    alias(SUBMIT_ELEMENT, EXECUTE_SCRIPT);

    defineCommand(EXECUTE_SCRIPT, post("/session/:sessionId/execute/sync"));
    defineCommand(EXECUTE_ASYNC_SCRIPT, post("/session/:sessionId/execute/async"));

    alias(GET_PAGE_SOURCE, EXECUTE_SCRIPT);

    defineCommand(MAXIMIZE_CURRENT_WINDOW, post("/session/:sessionId/window/maximize"));
    alias(GET_CURRENT_WINDOW_POSITION, EXECUTE_SCRIPT);
    alias(SET_CURRENT_WINDOW_POSITION, EXECUTE_SCRIPT);
    defineCommand(GET_CURRENT_WINDOW_SIZE, get("/session/:sessionId/window/size"));
    defineCommand(SET_CURRENT_WINDOW_SIZE, post("/session/:sessionId/window/size"));
    defineCommand(GET_CURRENT_WINDOW_HANDLE, get("/session/:sessionId/window"));
    defineCommand(GET_WINDOW_HANDLES, get("/session/:sessionId/window/handles"));

    defineCommand(ACCEPT_ALERT, post("/session/:sessionId/alert/accept"));
    defineCommand(DISMISS_ALERT, post("/session/:sessionId/alert/dismiss"));
    defineCommand(GET_ALERT_TEXT, get("/session/:sessionId/alert/text"));
    defineCommand(SET_ALERT_VALUE, post("/session/:sessionId/alert/text"));
  }

  @Override
  protected Map<String, ?> amendParameters(String name, Map<String, ?> parameters) {
    switch (name) {
      case FIND_CHILD_ELEMENT:
      case FIND_CHILD_ELEMENTS:
      case FIND_ELEMENT:
      case FIND_ELEMENTS:
        String using = (String) parameters.get("using");
        String value = (String) parameters.get("value");

        Map<String, Object> toReturn = new HashMap<>();
        toReturn.putAll(parameters);

        switch (using) {
          case "class name":
            toReturn.put("using", "css selector");
            toReturn.put("value", "." + cssEscape(value));
            break;

          case "id":
            toReturn.put("using", "css selector");
            toReturn.put("value", "#" + cssEscape(value));
            break;

          case "link text":
            // Do nothing
            break;

          case "name":
            toReturn.put("using", "css selector");
            toReturn.put("value", "*[name='" + value + "']");
            break;

          case "partial link text":
            // Do nothing
            break;

          case "tag name":
            toReturn.put("using", "css selector");
            toReturn.put("value", cssEscape(value));
            break;

          case "xpath":
            // Do nothing
            break;
        }
        return toReturn;

      case GET_ELEMENT_ATTRIBUTE:
        // Read the atom, wrap it, execute it.
        return executeAtom(
          "getAttribute.js",
          asElement(parameters.get("id")),
          parameters.get("name"));

      case GET_ELEMENT_LOCATION_ONCE_SCROLLED_INTO_VIEW:
        return toScript(
          "return arguments[0].getBoundingClientRect()",
          asElement(parameters.get("id")));

      case GET_PAGE_SOURCE:
        return toScript(
          "var source = document.documentElement.outerHTML; \n" +
          "if (!source) { source = new XMLSerializer().serializeToString(document); }\n" +
          "return source;");

      case GET_CURRENT_WINDOW_POSITION:
        return toScript("return {x: window.screenX, y: window.screenY}");

      case IS_ELEMENT_DISPLAYED:
        return executeAtom("isDisplayed.js", asElement(parameters.get("id")));

      case SET_CURRENT_WINDOW_POSITION:
        return toScript(
          "window.screenX = arguments[0]; window.screenY = arguments[1]",
          parameters.get("x"),
          parameters.get("y"));

      case SUBMIT_ELEMENT:
        return toScript(
          "var form = arguments[0];\n" +
          "while (form.nodeName != \"FORM\" && form.parentNode) {\n" +
          "  form = form.parentNode;\n" +
          "}\n" +
          "if (form == null) { throw Error('Unable to find containing form element'); }\n" +
          "var e = form.ownerDocument.createEvent('Event');\n" +
          "e.initEvent('submit', true, true);\n" +
          "if (form.dispatchEvent(e)) { form.submit() }\n",
          asElement(parameters.get("id")));

      default:
        return parameters;
    }
  }

  private Map<String, ?> executeAtom(String atomFileName, Object... args) {
    try {
      String scriptName = "/org/openqa/selenium/remote/" + atomFileName;
      URL url = getClass().getResource(scriptName);

      String rawFunction = Resources.toString(url, Charsets.UTF_8);
      String script = String.format(
        "return (%s).apply(null, arguments);",
        rawFunction);
      return toScript(script, args);
    } catch (IOException | NullPointerException e) {
      throw new WebDriverException(e);
    }
  }

  private Map<String, ?> toScript(String script, Object... args) {
    // Escape the quote marks
    script = script.replaceAll("\"", "\\\"");

    Iterable<Object> convertedArgs = Iterables.transform(
      Lists.newArrayList(args), new WebElementToJsonConverter());

    return ImmutableMap.of(
      "script", script,
      "args", Lists.newArrayList(convertedArgs));
  }

  private Map<String, String> asElement(Object id) {
    return ImmutableMap.of("element-6066-11e4-a52e-4f735466cecf", (String) id);
  }

  private String cssEscape(String using) {
    using = using.replaceAll("(['\"\\\\#.:;,!?+<>=~*^$|%&@`{}\\-\\/\\[\\]\\(\\)])", "\\\\$1");
    if (using.length() > 0 && Character.isDigit(using.charAt(0))) {
      using = "\\" + Integer.toString(30 + Integer.parseInt(using.substring(0,1))) + " " + using.substring(1);
    }
    return using;
  }
}
