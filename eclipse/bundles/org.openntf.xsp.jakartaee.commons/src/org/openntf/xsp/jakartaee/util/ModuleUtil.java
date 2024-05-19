/**
 * Copyright (c) 2018-2024 Contributors to the XPages Jakarta EE Support Project
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
package org.openntf.xsp.jakartaee.util;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.servlet.ServletException;

import org.osgi.framework.Bundle;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.domino.xsp.module.nsf.NSFComponentModule;
import com.ibm.domino.xsp.module.nsf.NSFService;
import com.ibm.domino.xsp.module.nsf.RuntimeFileSystem.NSFFile;

import jakarta.servlet.annotation.HandlesTypes;

/**
 * This class contains methods for working with {@link ComponentModule} instances.
 * 
 * @author Jesse Gallagher
 * @since 1.0.0
 */
public enum ModuleUtil {
	;
	
	private static final Logger log = Logger.getLogger(ModuleUtil.class.getName());
	
	private static final String PREFIX_CLASSES = "WEB-INF/classes/"; //$NON-NLS-1$
	private static final String SUFFIX_CLASS = ".class"; //$NON-NLS-1$

	/**
	 * A {@link Pattern} to match the names of classes generated by the XPages compilation process.
	 */
	public static final Pattern GENERATED_CLASSNAMES = Pattern.compile("^(xsp|plugin)\\..*$"); //$NON-NLS-1$
	
	public static Stream<String> getClassNames(ComponentModule module) {
		if(module instanceof NSFComponentModule) {
			return ((NSFComponentModule)module).getRuntimeFileSystem().getAllResources().entrySet().stream()
				.map(Map.Entry::getKey)
				.filter(key -> key.startsWith(PREFIX_CLASSES) && key.endsWith(SUFFIX_CLASS))
				.map(key -> key.substring(PREFIX_CLASSES.length(), key.length()-SUFFIX_CLASS.length()))
				.map(key -> key.replace('/', '.'));
		} else if(module == null) {
			return Stream.empty();
		} else {
			// TODO support other module types
			if(log.isLoggable(Level.WARNING)) {
				log.warning(MessageFormat.format("Unable to read class names from unsupported ComponentModule type {0}", module.getClass().getName()));
			}
			return Stream.empty();
		}
	}
	
	/**
	 * Retrieves a lazily-loaded stream of all classes stored in the provided module.
	 * 
	 * <p>This method skips known "generated" classes, such as the Java source generated
	 * for XPages.</p>
	 * 
	 * @param module the module to load from
	 * @return a {@link Stream} of {@link Class} objects
	 * @since 2.5.0
	 */
	public static Stream<Class<?>> getClasses(ComponentModule module) {
		ClassLoader cl = module.getModuleClassLoader();
		if(cl != null) {
			return getClassNames(module)
				.filter(className -> !GENERATED_CLASSNAMES.matcher(className).matches())
				.map(name -> {
					try {
						return Class.forName(name, true, cl);
					} catch (Throwable e) {
						log.log(Level.SEVERE, MessageFormat.format("Encountered exception loading class {0}", name), e);
						return (Class<?>)null;
					}
				})
				.filter(Objects::nonNull);
		} else {
			return Stream.empty();
		}
	}
	
	/**
	 * Lists files within the specified base path and subdirectories, returning
	 * just files and not directories.
	 * 
	 * @param module the {@link ComponentModule} to search
	 * @param basePath the base path to search, such as 
	 * @return a {@link Stream} of file names beneath the provided base path
	 * @since 2.12.0
	 */
	public static Stream<String> listFiles(ComponentModule module, String basePath) {
		String path = basePath;
		boolean listAll = StringUtil.isEmpty(basePath);
		if(!listAll && !path.endsWith("/")) { //$NON-NLS-1$
			path += "/"; //$NON-NLS-1$
		}
		
		if(module instanceof NSFComponentModule) {
			return ((NSFComponentModule)module).getRuntimeFileSystem().getAllResources().entrySet().stream()
				.filter(entry -> entry.getValue() instanceof NSFFile)
				.map(Map.Entry::getKey)
				.filter(key -> listAll || key.startsWith(basePath));
		} else if(module == null) {
			return Stream.empty();
		} else {
			// TODO support other module types
			if(log.isLoggable(Level.WARNING)) {
				log.warning(MessageFormat.format("Unable to read file names from unsupported ComponentModule type {0}", module.getClass().getName()));
			}
			return Stream.empty();
		}
	}
	
	/**
	 * Derives a useful ID for the provided module.
	 * 
	 * <p>In the case of an NSF, this will be the database path. Otherwise,
	 * it will be the object ID of the module.</p>
	 * 
	 * @param module the module to derive an ID for
	 * @return a useful ID value for the module
	 * @since 1.13.0
	 */
	public static String getModuleId(ComponentModule module) {
		if(module instanceof NSFComponentModule) {
			return ((NSFComponentModule)module).getDatabasePath();
		} else {
			return Integer.toHexString(System.identityHashCode(module));
		}
	}
	
	/**
	 * Builds a collection of classes based on the rules defined in the provided
	 * {@code HandlesTypes} annotation, optionally reading classes from some
	 * bundles.
	 * 
	 * @param types the {@link HandlesTypes} annotation to consult
	 * @param module the {@link ComponentModule} to scan
	 * @param bundles any {@link Bundle}s to scan to add to the collection
	 * @return a {@link Set} of matching {@link Class} instances, or {@code null}
	 *         if none are found
	 * @since 2.13.0
	 */
	@SuppressWarnings("unchecked")
	public static Set<Class<?>> buildMatchingClasses(HandlesTypes types, ComponentModule module, Bundle... bundles) {
		Set<Class<?>> result = new HashSet<>();
		ModuleUtil.getClassNames(module)
			.filter(className -> !ModuleUtil.GENERATED_CLASSNAMES.matcher(className).matches())
			.map(className -> {
				try {
					return Class.forName(className, true, module.getModuleClassLoader());
				} catch (ClassNotFoundException | NoClassDefFoundError e) {
					throw new RuntimeException(MessageFormat.format("Encountered exception processing class {0} in {1}", className, getModuleId(module)), e);
				}
			}).filter(c -> {
				for (Class<?> type : types.value()) {
					if (type.isAnnotation()) {
						return c.isAnnotationPresent((Class<? extends Annotation>) type);
					} else {
						return type.isAssignableFrom(c);
					}
				}
				return true;
			}).forEach(result::add);

		for(Bundle bundle : bundles) {
			String baseUrl = bundle.getEntry("/").toString(); //$NON-NLS-1$
			List<URL> entries = Collections.list(bundle.findEntries("/", "*.class", true)); //$NON-NLS-1$ //$NON-NLS-2$
			entries.stream()
				.parallel()
				.map(String::valueOf)
				.map(url -> url.substring(baseUrl.length()))
				.map(LibraryUtil::toClassName)
				.filter(StringUtil::isNotEmpty)
				.sequential()
				.map(className -> {
					try {
						return bundle.loadClass(className);
					} catch (ClassNotFoundException e) {
						throw new RuntimeException(e);
					}
				}).filter(c -> {
					for (Class<?> type : types.value()) {
						if (type.isAnnotation()) {
							return c.isAnnotationPresent((Class<? extends Annotation>) type);
						} else {
							return type.isAssignableFrom(c);
						}
					}
					return true;
				}).forEach(result::add);
		}
		
		if (!result.isEmpty()) {
			return result;
		} else {
			return null;
		}
	}
	
	/**
	 * Attempts to find or load the {@link ComponentModule} for the given
	 * NSF path.
	 * 
	 * @param nsfPath the NSF path to load, e.g. {@code "foo/bar.nsf"}
	 * @return an {@link Optional} describing the {@link ComponentModule}
	 *         if available, or an empty one if there is no such NSF
	 * @since 2.13.0
	 */
	public static Optional<ComponentModule> getNSFComponentModule(String nsfPath) {
		LCDEnvironment lcd = LCDEnvironment.getInstance();
		NSFService nsfService = lcd.getServices().stream()
			.filter(NSFService.class::isInstance)
			.map(NSFService.class::cast)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Unable to locate active NSFService"));
		try {
			return Optional.ofNullable(nsfService.loadModule(nsfPath));
		} catch(ServletException e) {
			throw new RuntimeException(e);
		}
	}

}
