package com.thetdgroup.transform;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.Source;

import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jdom.output.DOMOutputter;

public class SourceDocumentTransform
{
 public static String transform(final SAXBuilder saxBuilder,
																													 		final DOMOutputter domOutputter,
																													 		final String documentTransform,
																													 		final String documentData,
																													 		final String documentLanguage) throws Exception
 {
 	String htmlData = "";

 	//
 	StringWriter stringWriter = null;
 	
 	try
 	{
	 	StringBuffer stringBuffer = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			stringBuffer.append("<document_data>");
			stringBuffer.append(documentData);
			stringBuffer.append("</document_data>");
	 	
			//
			BufferedReader bufferedReader = new BufferedReader(new StringReader(stringBuffer.toString()));
			Document htmlDocument = saxBuilder.build(bufferedReader);
			
			//
			org.w3c.dom.Document domDocument = domOutputter.output(htmlDocument);
			Source xmlSource = new DOMSource(domDocument);
	  
			// Run the XSLT transform
			StreamSource xsltSource = new StreamSource(new FileInputStream(documentTransform));
			TransformerFactory tFactory = TransformerFactory.newInstance();
			
			Transformer transformer = tFactory.newTransformer(xsltSource);
			transformer.setParameter("language_id", documentLanguage);
			
			// Get result into a string
			stringWriter = new StringWriter();
			transformer.transform(xmlSource, new StreamResult(stringWriter));
	
			htmlData = stringWriter.toString();
 	}
 	catch(Exception exception)
 	{
 		System.out.println(exception.toString());
 		throw exception;
 	}
 	finally
 	{
			stringWriter.close();
 	}
 	
 	//
 	return htmlData;
 }
}
