package com.thetdgroup.parsers;

import java.net.URLDecoder;

import org.jdom.input.SAXBuilder;
import org.jdom.output.DOMOutputter;
import org.jdom.output.XMLOutputter;
import org.json.JSONObject;

import com.thetdgroup.TranslationAdapter;
import com.thetdgroup.TranslationMetadata;
import com.thetdgroup.TranslationStatus;
import com.thetdgroup.transform.SourceDocumentTransform;

public final class TranslationParser
{
	private String documentParentPath = "";
	private String documentName = "";
	private String documentStatus = "";
	private String documentDate = "";

	private String documentOriginalAuthor = "";

	private String documentSourceLanguage = "";
	private String documentTranslationLanguage = "";
	
	private String documentSourceData = "";
	private String annotatedDocumentWordLevel = "";
	private String annotatedDocumentSentenceLevel = "";
	private String annotatedDocumentParagraphLevel = "";
	
	//
	public void parse(final SAXBuilder saxBuilder, 
																			final DOMOutputter xmlOutputter, 
																			final String documentTransform,
																			final JSONObject jsonInputTerminology) throws Exception
	{
		//
		documentParentPath = jsonInputTerminology.getString("parent_node_path");
		documentName = jsonInputTerminology.getString("node_name");
		documentDate = jsonInputTerminology.getString("node_created_date");

		//
		documentOriginalAuthor = jsonInputTerminology.getString(TranslationMetadata.TRANSLATION_ORIGINAL_AUTHOR);
		documentStatus = jsonInputTerminology.getString(TranslationStatus.TRANSLATION_STATUS);
		documentSourceLanguage = jsonInputTerminology.getString(TranslationMetadata.DOCUMENT_SOURCE_LANGUAGE);
		//documentTranslationLanguage = jsonInputTerminology.getString(TranslationMetadata.DOCUMENT_TRANSLATION_LANGUAGE);
		
		//
		documentSourceData = jsonInputTerminology.getString("content_data");
		String wordData = jsonInputTerminology.getString(TranslationMetadata.ANNOTATED_DOCUMENT_WORD_LEVEL);
		
		annotatedDocumentWordLevel = SourceDocumentTransform.transform(saxBuilder, 
				                                                             xmlOutputter,
				                                                             documentTransform,
																																																																	URLDecoder.decode(wordData, "UTF-8"), 
																																																																	documentSourceLanguage);
		
		//annotatedDocumentSentenceLevel = jsonInputTerminology.getString(TranslationMetadata.ANNOTATED_DOCUMENT_SENTENCE_LEVEL);
		//annotatedDocumentParagraphLevel = jsonInputTerminology.getString(TranslationMetadata.ANNOTATED_DOCUMENT_PARAGRAPH_LEVEL);
	}
	
	public JSONObject toJSON() throws Exception
	{
		JSONObject jsonObject = new JSONObject();
		
		//
		jsonObject.put("document_folder", documentParentPath);
		jsonObject.put("document_name", documentName);
		jsonObject.put("document_date", documentDate);
		
		//
		jsonObject.put("document_original_author", documentOriginalAuthor);
		jsonObject.put("document_status", documentStatus);
		jsonObject.put("document_source_language", documentSourceLanguage);
		jsonObject.put("document_translation_language", documentTranslationLanguage);
		
		//
		jsonObject.put("document_annotated_data", annotatedDocumentWordLevel);
		
		//
		return jsonObject;
	}
}
