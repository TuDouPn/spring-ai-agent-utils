/*
* Copyright 2026 - 2026 the original author or authors.
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
package org.springaicommunity.agent.exec.docker;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps {@code docker} CLI arguments intact on Windows.
 *
 * <p>
 * On Windows a process receives a single command-line string, which the JDK assembles
 * from the argv list and {@code docker.exe} splits again using the Microsoft C runtime
 * rules. In its default ("legacy") mode the JDK wraps arguments containing whitespace in
 * quotes but does not escape interior {@code "} characters, and passes an argument that
 * already starts and ends with {@code "} through unchanged. Arguments such as the PID
 * wrapper ({@code echo $$ >"$1"; shift; exec "$@"}) or a command like
 * {@code echo "EXIT=$?"} therefore lose their quotes: the unquoted {@code $@} re-splits
 * the command, which silently runs a bare {@code echo} instead.
 *
 * <p>
 * This class quotes every argument that contains a {@code "} using the Microsoft rules, so
 * the JDK passes it through and {@code docker.exe} recovers the original value. It is a
 * no-op on other platforms, and when {@code jdk.lang.Process.allowAmbiguousCommands} is
 * {@code false} (the JDK then escapes interior quotes itself and rejects pre-quoted
 * arguments).
 *
 * @author Christian Tzolov
 */
final class WindowsCommandLine {

	private WindowsCommandLine() {
	}

	/** Returns the argv to hand to {@link ProcessBuilder} on the current platform. */
	static List<String> prepare(List<String> argv) {
		return needsQuoting() ? quote(argv) : argv;
	}

	static boolean needsQuoting() {
		return System.getProperty("os.name", "").startsWith("Windows")
				&& !"false".equalsIgnoreCase(System.getProperty("jdk.lang.Process.allowAmbiguousCommands", "true"));
	}

	/** Quotes each argument after the executable that contains a {@code "}. */
	static List<String> quote(List<String> argv) {
		List<String> quoted = new ArrayList<>(argv.size());
		for (int i = 0; i < argv.size(); i++) {
			String arg = argv.get(i);
			quoted.add((i > 0 && arg.indexOf('"') >= 0) ? quoteArgument(arg) : arg);
		}
		return quoted;
	}

	/**
	 * Quotes one argument with the Microsoft C runtime rules: backslashes are literal
	 * unless they precede a {@code "}, so backslashes before a quote (or before the closing
	 * quote) are doubled and every {@code "} is escaped as {@code \"}.
	 */
	static String quoteArgument(String arg) {
		StringBuilder sb = new StringBuilder(arg.length() + 8).append('"');
		int backslashes = 0;
		for (int i = 0; i < arg.length(); i++) {
			char c = arg.charAt(i);
			if (c == '\\') {
				backslashes++;
				continue;
			}
			if (c == '"') {
				sb.append("\\".repeat(backslashes * 2 + 1));
			}
			else {
				sb.append("\\".repeat(backslashes));
			}
			backslashes = 0;
			sb.append(c);
		}
		return sb.append("\\".repeat(backslashes * 2)).append('"').toString();
	}

}
