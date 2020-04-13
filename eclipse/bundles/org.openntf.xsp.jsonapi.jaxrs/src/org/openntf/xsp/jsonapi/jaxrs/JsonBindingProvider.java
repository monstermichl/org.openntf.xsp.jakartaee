package org.openntf.xsp.jsonapi.jaxrs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import org.jboss.weld.proxy.WeldClientProxy;
import org.jboss.weld.proxy.WeldClientProxy.Metadata;
import org.openntf.xsp.jsonapi.JSONBindUtil;

@Produces({"application/json", "application/*+json", "text/json", "*/*"})
@Consumes({"application/json", "application/*+json", "text/json", "*/*"})
public class JsonBindingProvider implements MessageBodyWriter<Object>, MessageBodyReader<Object> {
	protected Jsonb getJsonb(Class<?> type) {
		return JsonbBuilder.create();
	}

	// *******************************************************************************
	// * MessageBodyReader
	// *******************************************************************************

	@Override
	public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return isSupportedMediaType(mediaType);
	}

	@Override
	public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
			throws IOException, WebApplicationException {
		Jsonb jsonb = getJsonb(type);
		return JSONBindUtil.fromJson(entityStream, jsonb, genericType);
	}
	
	// *******************************************************************************
	// * MessageBodyWriter
	// *******************************************************************************

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return isSupportedMediaType(mediaType);
	}
	
	@Override
	public long getSize(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
			throws IOException, WebApplicationException {
		
		Object obj = t;
		// It may be a CDI proxy - try to unwrap it if so
		if(obj instanceof WeldClientProxy) {
			Metadata meta = ((WeldClientProxy)obj).getMetadata();
			obj = meta.getContextualInstance();
		}
		
		Jsonb jsonb = getJsonb(type);
		JSONBindUtil.toJson(obj, jsonb, entityStream);
		entityStream.flush();
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private static final String JSON = "json"; //$NON-NLS-1$
	private static final String PLUS_JSON = "+json"; //$NON-NLS-1$
	
	public static boolean isSupportedMediaType(final MediaType mediaType) {
		return mediaType.getSubtype().equals(JSON) || mediaType.getSubtype().endsWith(PLUS_JSON);
	}

}
