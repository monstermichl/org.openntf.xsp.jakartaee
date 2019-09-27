/**
 * Copyright © 2019 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.xsp.jakartaee;

import com.ibm.domino.xsp.module.nsf.NSFComponentModule;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This class contains methods for working with {@link NSFComponentModule} instances.
 * 
 * @author Jesse Gallagher
 * @since 1.0.0
 */
public enum ModuleUtil {
	;
	
	private static final String PREFIX_CLASSES = "WEB-INF/classes/"; //$NON-NLS-1$
	private static final String SUFFIX_CLASS = ".class"; //$NON-NLS-1$

	/**
	 * A {@link Pattern} to match the names of classes generated by the XPages compilation process.
	 */
	public static final Pattern GENERATED_CLASSNAMES = Pattern.compile("^(xsp|plugin)\\..*$"); //$NON-NLS-1$
	
	public static Stream<String> getClassNames(NSFComponentModule module) {
		return module.getRuntimeFileSystem().getAllResources().entrySet().stream()
			.map(Map.Entry::getKey)
			.filter(key -> key.startsWith(PREFIX_CLASSES) && key.endsWith(SUFFIX_CLASS))
			.map(key -> key.substring(PREFIX_CLASSES.length(), key.length()-SUFFIX_CLASS.length()))
			.map(key -> key.replace('/', '.'));
	}

}
