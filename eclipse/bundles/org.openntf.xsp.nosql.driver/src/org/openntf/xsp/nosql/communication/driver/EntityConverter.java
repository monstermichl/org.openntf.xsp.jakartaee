/**
 * Copyright © 2018-2022 Jesse Gallagher
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
package org.openntf.xsp.nosql.communication.driver;


import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.nosql.document.Document;
import jakarta.nosql.document.DocumentEntity;
import lotus.domino.Database;
import lotus.domino.DocumentCollection;
import lotus.domino.Item;
import lotus.domino.NotesException;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

public class EntityConverter {
	/**
	 * The field used to store the UNID of the document during JSON
	 * serialization, currently {@value #ID_FIELD}
	 */
	public static final String ID_FIELD = "_id"; //$NON-NLS-1$
	/**
	 * The expected field containing the collection name of the document in
	 * Domino, currently {@value #NAME_FIELD}
	 */
	public static final String NAME_FIELD = "Form"; //$NON-NLS-1$

	private EntityConverter() {
	}
	
	static Stream<DocumentEntity> convert(Database database, String qrpJson) throws NotesException {
		JsonObject json;
		try(
			StringReader r = new StringReader(qrpJson);
			JsonReader reader = Json.createReader(r)
		) {
			json = reader.readObject();
		}
		JsonArray results = json.getJsonArray("StreamResults"); //$NON-NLS-1$
		return results.stream()
			.map(JsonValue::asJsonObject)
			.map(entry -> entry.getString("@nid")) //$NON-NLS-1$
			.map(noteId -> {
				try {
					return database.getDocumentByID(noteId.substring(2));
				} catch (NotesException e) {
					throw new RuntimeException(e);
				}
			})
			.filter(EntityConverter::isValid)
			.map(doc -> {
				try {
					List<Document> documents = toDocuments(doc);
					String name = doc.getItemValueString(NAME_FIELD);
					return DocumentEntity.of(name, documents);
				} catch(NotesException e) {
					throw new RuntimeException("Exception processing note " + doc, e);
				}
			});
	}
	
	static Stream<DocumentEntity> convert(Database database, View docs) throws NotesException {
		// TODO stream this better
		// TODO create a lazy-loading list?
		List<DocumentEntity> result = new ArrayList<>();
		ViewNavigator nav = docs.createViewNav();
		try {
			ViewEntry entry = nav.getFirst();
			while(entry != null) {
				List<?> columnValues = entry.getColumnValues();
				// The last column is the note ID in format "NT00000000"
				String noteId = (String)columnValues.get(columnValues.size()-1);
				lotus.domino.Document doc = database.getDocumentByID(noteId.substring(2));
				if(isValid(doc)) {
					List<Document> documents = toDocuments(doc);
					String name = doc.getItemValueString(NAME_FIELD);
					result.add(DocumentEntity.of(name, documents));
				}
				
				ViewEntry tempEntry = entry;
				entry = nav.getNext(entry);
				tempEntry.recycle();
			}
		} finally {
			nav.recycle();
		}
		return result.stream();
	}

	static Stream<DocumentEntity> convert(DocumentCollection docs) throws NotesException {
		// TODO stream this better
		// TODO create a lazy-loading list?
		List<DocumentEntity> result = new ArrayList<>();
		lotus.domino.Document doc = docs.getFirstDocument();
		while(doc != null) {
			if(isValid(doc)) {
				List<Document> documents = toDocuments(doc);
				String name = doc.getItemValueString(NAME_FIELD);
				result.add(DocumentEntity.of(name, documents));
			}
			
			lotus.domino.Document tempDoc = doc;
			doc = docs.getNextDocument();
			tempDoc.recycle();
		}
		return result.stream();
	}

	@SuppressWarnings("unchecked")
	public static List<Document> toDocuments(lotus.domino.Document doc) throws NotesException {
		List<Document> result = new ArrayList<>();
		result.add(Document.of(ID_FIELD, doc.getUniversalID()));
		Map<String, Object> docMap = new LinkedHashMap<>();
		for(Item item : (List<Item>)doc.getItems()) {
			String itemName = item.getName();
			if(NAME_FIELD.equalsIgnoreCase(itemName)) {
				continue;
			}
			List<?> val = item.getValues();
			if(val.isEmpty()) {
				docMap.put(item.getName(), null);
			} else if(val.size() == 1) {
				docMap.put(item.getName(), val.get(0));
			} else {
				docMap.put(item.getName(), val);
			}
		}
		
		docMap.forEach((key, value) -> result.add(Document.of(key, value)));

		result.add(Document.of("_cdate", doc.getCreated().toJavaDate().getTime())); //$NON-NLS-1$
		result.add(Document.of("_mdate", doc.getCreated().toJavaDate().getTime())); //$NON-NLS-1$
		
		// TODO attachments support
//		result.add(Document.of(ATTACHMENT_FIELD,
//			Stream.of(doc.getAttachments())
//				.map(t -> {
//					try {
//						return new DominoDocumentAttachment(t);
//					} catch (NotesException e) {
//						throw new RuntimeException(e);
//					}
//				})
//				.collect(Collectors.toList())
//		));
		
		return result;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static List<Document> toDocuments(Map<String, Object> map) {
		List<Document> documents = new ArrayList<>();
		for (String key : map.keySet()) {
			if(key == null || key.isEmpty()) {
				continue;
			}
			
			Object value = map.get(key);
			if (value instanceof Map) {
				documents.add(Document.of(key, toDocuments((Map) value)));
			} else if (isADocumentIterable(value)) {
				List<List<Document>> subDocuments = new ArrayList<>();
				stream(((Iterable) value).spliterator(), false)
					.map(m -> toDocuments((Map) m))
					.forEach(e -> subDocuments.add((List<Document>)e));
				documents.add(Document.of(key, subDocuments));
			} else if(value != null) {
				documents.add(Document.of(key, value));
			}
		}
		return documents;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static boolean isADocumentIterable(Object value) {
		return value instanceof Iterable && stream(((Iterable) value).spliterator(), false).allMatch(Map.class::isInstance);
	}

	/**
	 * Converts the provided {@link DocumentEntity} instance into a Domino
	 * JSON object.
	 * 
	 * <p>This is equivalent to calling {@link #convert(DocumentEntity, boolean)} with
	 * <code>false</code> as the second parameter.</p>
	 * 
	 * @param entity the entity instance to convert
	 * @param target the target Domino Document to store in
	 */
	public static void convert(DocumentEntity entity, lotus.domino.Document target) throws NotesException {
		convert(entity, false, target);
	}
	
	/**
	 * Converts the provided {@link DocumentEntity} instance into a Domino
	 * JSON object.
	 * 
	 * @param entity the entity instance to convert
	 * @param retainId whether or not to remove the {@link #ID_FIELD} field during conversion
	 * @param target the target Domino Document to store in
	 */
	public static void convert(DocumentEntity entity, boolean retainId, lotus.domino.Document target) throws NotesException {
		requireNonNull(entity, "entity is required"); //$NON-NLS-1$

		for(Document doc : entity.getDocuments()) {
			if(!"$FILE".equalsIgnoreCase(doc.getName()) && !ID_FIELD.equalsIgnoreCase(doc.getName())) { //$NON-NLS-1$
				Object value = doc.get();
				if(value == null) {
					target.removeItem(doc.getName());
				} else {
					target.replaceItemValue(doc.getName(), toDominoFriendly(target, value)).recycle();
				}
			}
		}
		
		target.replaceItemValue(NAME_FIELD, entity.getName());
	}
	
	private static Object toDominoFriendly(lotus.domino.Document context, Object value) throws NotesException {
		if(value instanceof Iterable) {
			Vector<Object> result = new Vector<Object>();
			for(Object val : (Iterable<?>)value) {
				result.add(toDominoFriendly(context, val));
			}
			return result;
		} else if(value instanceof Date) {
			return context.getParentDatabase().getParent().createDateTime((Date)value);
		} else if(value instanceof Calendar) {
			return context.getParentDatabase().getParent().createDateTime((Calendar)value);
		} else if(value instanceof Number) {
			return ((Number)value).doubleValue();
		} else if(value instanceof Boolean) {
			// TODO figure out if this can be customized, perhaps from the Settings element
			return (Boolean)value ? "Y": "N"; //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			// TODO support other types above
			return value.toString();
		}
	}
	
	private static boolean isValid(lotus.domino.Document doc) {
		try {
			return doc != null && doc.isValid() && !doc.isDeleted() && doc.getCreated() != null;
		} catch (NotesException e) {
			throw new RuntimeException(e);
		}
	}
}
