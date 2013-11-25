package com.thetdgroup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.xpath.XPath;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.thetdgroup.AdapterConstants;
import com.thetdgroup.ServiceConstants;
import com.thetdgroup.parsers.TranslationParser;
import com.thetdgroup.util.xml.XMLUtil;

//
public final class TranslationAdapter extends BaseTranslationAdapter
{
	private static XMLReader documentValidator = null;
	private static String documentMimetype = "";
	private static String sourceDocumentTransform = "";
	private static String namedEntitiesTransform = "";
	
	private static String documentSchemaName = ""; 
	private static String documentSchemaData = ""; 
	
	private FuzeInCommunication fuzeInCommunication = new FuzeInCommunication();

	//
	public void initialize(final JSONObject configurationObject) throws Exception
	{
		if(configurationObject.has("adapter_configuration_file") == false)
		{
			throw new Exception("The adapter_configuration_file parameter was not found");
		}
		
		// Set FuzeIn connection
		if(configurationObject.has("fuzein_connection_info"))
		{
			JSONObject jsonCommParams = configurationObject.getJSONObject("fuzein_connection_info");
			
			fuzeInCommunication.setFuzeInConnection(jsonCommParams.getString("service_url"), 
																																											jsonCommParams.getInt("service_socket_timeout"), 
																																											jsonCommParams.getInt("service_connection_timeout"), 
																																											jsonCommParams.getInt("service_connection_retry"));
		}
		
		//
		parseAdapterConfiguration(configurationObject.getString("adapter_configuration_file"));
	}
	
	//
	public void destroy()
	{
	
	}
	
	//
	public JSONObject saveDocument(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		//
		// Validate that all required params are present
		//
		if(jsonData.has("submittal_type") == false)
		{
			throw new Exception("The 'submittal_type' parameter is required.");
		}
		
		if(jsonData.has("content_management_folder") == false)
		{
			throw new Exception("The 'content_management_folder' parameter is required.");
		}
		
		if(jsonData.has("user_information") == false)
		{
			throw new Exception("The 'user_information' object is required.");
		}
  		
		if(jsonData.has("document_data") == false)
		{
			throw new Exception("The 'document_data' object is required.");
		}
  
		//
		String translationDocumentName = "";
		String translationDocumentData = "";
		
		//
		// Process Document based on submittal type
		//
		String submittalType = jsonData.getString("submittal_type");
		
		if(submittalType.equals("file"))
		{
			if(jsonData.has("document_name") == false || jsonData.getString("document_name").length() == 0)
			{
				throw new Exception("A Document name is required.");
			}
			
			//
			// Read file
			InputStream inputStream = null;
			BOMInputStream bomInputStream = null;
			
			try
			{
				inputStream = new FileInputStream(jsonData.getString("document_data"));
		 	bomInputStream = new BOMInputStream(inputStream, false);
		 	
		 	//
				String xmlString = IOUtils.toString(bomInputStream, "UTF-8");
				
				if(xmlString.length() == 0)
				{
					xmlString = jsonData.getString("document_data");
				}
				
				// 
				submittalType = "string";
				translationDocumentName = jsonData.getString("document_name");
				translationDocumentData = xmlString;
			}
			catch(Exception exception)
			{
				throw new Exception(exception);
			}
			finally
			{
				if(bomInputStream != null)
				{
					bomInputStream.close();
				}
				
				if(inputStream != null)
				{
					inputStream.close();
				}
			}
		}
		else if(submittalType.equals("xml"))
		{
			if(jsonData.has("document_name") == false || jsonData.getString("document_name").length() == 0)
			{
				throw new Exception("A Document name is required.");
			}
			
			//
			InputStream inputStream = null;
			BOMInputStream bomInputStream = null;
			ByteArrayInputStream byteArrayInputStream = null;
			
			try
			{
				String xmlString = jsonData.getString("document_data");
		
				//
				// Validate against LMF schema
			 Document documentDocument = saxBuilder.build(bomInputStream);
				validateTranslation(XMLUtil.compactDocumentString(documentDocument));

				// 
				submittalType = "string";
				translationDocumentName = jsonData.getString("document_name");
				translationDocumentData = xmlString;
			}
			catch(Exception exception)
			{
				throw new Exception(exception);
			}
			finally
			{
				if(bomInputStream != null)
				{
					bomInputStream.close();
				}
				
				if(inputStream != null)
				{
					inputStream.close();
				}
			}
		}
		else if(submittalType.equals("json"))
		{

		}
		else if(submittalType.equals("stream"))
		{

		}
		else
		{
			throw new Exception("The 'submittal_type' parameter is invalid. Only 'file', 'xml', json' or 'stream' are supported.");
		}
		
		//
		// If Document language was specified
		String documentLanguage = "";
		
		if(jsonData.has("document_language") == true)
		{
			documentLanguage = jsonData.getString("document_language");
		}
		else
		{
			// Identify the Document Language...
			documentLanguage = Languages.ENGLISH;
		}
		
		//
		// Submit to the Semantic Analyzer
		JSONObject serviceAPIData = new JSONObject();
		serviceAPIData.put("document_language", documentLanguage);
		serviceAPIData.put("document_data", translationDocumentData);
		serviceAPIData.put("annotation_level", "word");
		
		JSONObject jsonSemanticObject = new JSONObject();
		jsonSemanticObject.put("service_meta", "fuzein_semantic_analyzer_service");
		jsonSemanticObject.put("service_action", "tokenize_string");
		jsonSemanticObject.put("service_api_data", serviceAPIData);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonSemanticObject);
		JSONObject jsonResponse = processServiceResponse(serviceResponse);
		
		//
		// Process Service Response
		String markedUpData_WordLevel = "";
		
		if(jsonResponse.getString(AdapterConstants.ADAPTER_STATUS).equals(AdapterConstants.status.SUCCESS.toString()))
		{
			markedUpData_WordLevel = jsonResponse.getJSONObject(AdapterConstants.ADAPTER_DATA).getString("annotated_data");
		}
		
		//
		// Extract User Information
		JSONObject jsonUserInformation = jsonData.getJSONObject("user_information");
		
		//
		// Add Metadata
		JSONObject jsonMetadataObject = new JSONObject();
	 jsonMetadataObject.put(TranslationMetadata.TRANSLATION_ORIGINAL_AUTHOR, jsonUserInformation.getString("user_name")); 
		jsonMetadataObject.put(TranslationStatus.TRANSLATION_STATUS, TranslationStatus.status.NEW_TRANSLATION);
		jsonMetadataObject.put(TranslationMetadata.DOCUMENT_SOURCE_LANGUAGE, documentLanguage);
		
  //
		// Add Marked Up Document
		jsonMetadataObject.put(TranslationMetadata.ANNOTATED_DOCUMENT_WORD_LEVEL, markedUpData_WordLevel);
		
		//
		// Add Schema Attachment
		JSONObject jsonAttachmentsObject = new JSONObject();
		//jsonAttachmentsObject(TranslationMetadata.TRANSLATION_SUPPORTING_SCHEMA_NAME, documentDescriptor.getString("document_schema_name"));
		//jsonAttachmentsObject(TranslationMetadata.TRANSLATION_SUPPORTING_SCHEMA, documentDescriptor.getString("document_schema_name"));
		
		//
		// Build CM Object
		JSONObject jsonFileObject = new JSONObject();
		jsonFileObject.put("parent_folder", jsonData.getString("content_management_folder"));
		jsonFileObject.put("node_name", translationDocumentName);		
		jsonFileObject.put("submittal_type", submittalType);
		jsonFileObject.put("data", translationDocumentData);
		jsonFileObject.put("fuzein_mimetype", documentMimetype);
		jsonFileObject.put("document_metadata", jsonMetadataObject);
		jsonFileObject.put("document_attachments", jsonAttachmentsObject);
		
		//
		// Submit to the Content Management Service
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "add_content");
		jsonMessageObject.put("service_api_data", jsonFileObject);
		
		serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);
		jsonResponse = processServiceResponse(serviceResponse);
		
		//
		// Process response
		JSONObject jsonTranslationObject = new JSONObject();
		
		if(jsonResponse.getString(AdapterConstants.ADAPTER_STATUS).equals(AdapterConstants.status.SUCCESS.toString()))
		{
			JSONObject jsonDataObject = jsonResponse.getJSONArray(AdapterConstants.ADAPTER_DATA).getJSONObject(0);

			//
			jsonTranslationObject.put("translation_status", jsonDataObject.getString("translation_status"));
			jsonTranslationObject.put("document_original_author", jsonDataObject.getString("translation_original_author"));
			jsonTranslationObject.put("document_date", jsonDataObject.getString("node_created_date"));
			jsonTranslationObject.put("document_name", jsonDataObject.getString("node_name"));
			jsonTranslationObject.put("document_folder", jsonDataObject.getString("parent_node_path"));
			//jsonTranslationObject.put("document_schema_name", jsonDataObject.getString("document_original_author"));
		}
		
		//
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
		jsonObject.put(AdapterConstants.ADAPTER_DATA, jsonTranslationObject);

		return jsonObject;
	}
	
	//
	public JSONObject updateDocument(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		//
		// Validate that all required params are present
		//
		if(jsonData.has("submittal_type") == false)
		{
			throw new Exception("The 'submittal_type' parameter is required.");
		}
		
		if(jsonData.has("content_management_folder") == false)
		{
			throw new Exception("The 'content_management_folder' parameter is required.");
		}
		
		if(jsonData.has("user_information") == false)
		{
			throw new Exception("The 'user_information' object is required.");
		}
  		
		if(jsonData.has("document_name") == false)
		{
			throw new Exception("The 'document_name' parameter is required.");
		}
		
		if(jsonData.has("document_data") == false)
		{
			throw new Exception("The 'document_data' parameter is required.");
		}
  
		//
		String documentDocumentData = "";
		
		//
		// Process Document based on submittal type
		//
		String submittalType = jsonData.getString("submittal_type");
		
		if(submittalType.equals("file"))
		{
			//
			// Read file
			File file = new File(jsonData.getString("document_data"));
			Document documentDocument = saxBuilder.build(file);
			
			//
			// Validate against the LMF schema
			validateTranslation(XMLUtil.compactDocumentString(documentDocument));
			
			// 
			submittalType = "string";
			documentDocumentData = XMLUtil.prettyDocumentString(documentDocument);
		}
		else if(submittalType.equals("xml"))
		{
			//
			// Read XML String
			ByteArrayInputStream byteArrayInputStream = null;
			Document documentDocument = null;
			
			try
			{
				String xmlString = jsonData.getString("document_data");
			 byteArrayInputStream = new ByteArrayInputStream(xmlString.getBytes("UTF-8"));
			 documentDocument = saxBuilder.build(byteArrayInputStream);
				
				//
				// Validate against the LMF schema
				validateTranslation(XMLUtil.compactDocumentString(documentDocument));
				
				// 
				submittalType = "string";
				documentDocumentData = xmlString;
			}
			catch(Exception exception)
			{
				throw new Exception(exception);
			}
			finally
			{
			 byteArrayInputStream.close();
			}
		}
		else if(submittalType.equals("json"))
		{

		}
		else if(submittalType.equals("stream"))
		{

		}
		else
		{
			throw new Exception("The 'submittal_type' parameter is invalid. Only 'file', 'xml', json' or 'stream' are supported.");
		}
		
		//
		// Extract User Information
		JSONObject jsonUserInformation = jsonData.getJSONObject("user_information");
		
		//
		// Add Translation Metadata
		JSONObject jsonMetadataObject = new JSONObject();
	 jsonMetadataObject.put(TranslationMetadata.TRANSLATION_MODIFICATION_AUTHOR, jsonUserInformation.getString("user_name")); 
		jsonMetadataObject.put(TranslationStatus.TRANSLATION_STATUS, TranslationStatus.status.UPDATED_TRANSLATION);

		//
		// Add Schema Attachment
		JSONObject jsonAttachmentsObject = new JSONObject();
		//jsonAttachmentsObject(TranslationMetadata.TRANSLATION_SUPPORTING_SCHEMA_NAME, documentDescriptor.getString("document_schema_name"));
		//jsonAttachmentsObject(TranslationMetadata.TRANSLATION_SUPPORTING_SCHEMA, documentDescriptor.getString("document_schema_name"));
		
		//
		// Build CM Object
		JSONObject jsonFileObject = new JSONObject();
		jsonFileObject.put("node_path", jsonData.getString("content_management_folder") + "/" + jsonData.getString("document_name"));
		jsonFileObject.put("submittal_type", submittalType);
		jsonFileObject.put("data", documentDocumentData);
		jsonFileObject.put("fuzein_mimetype", documentMimetype);
		jsonFileObject.put("document_metadata", jsonMetadataObject);
		jsonFileObject.put("document_attachments", jsonAttachmentsObject);
		
		//
		// Submit to the Content Management Service
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "update_content");
		jsonMessageObject.put("service_api_data", jsonFileObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);
		JSONObject jsonResponse = processServiceResponse(serviceResponse);

		//
		// Process response
		JSONObject jsonTranslationObject = new JSONObject();
		
		if(jsonResponse.getString(AdapterConstants.ADAPTER_STATUS).equals(AdapterConstants.status.SUCCESS.toString()))
		{
			JSONObject jsonDataObject = jsonResponse.getJSONArray(AdapterConstants.ADAPTER_DATA).getJSONObject(0);

			//
			jsonTranslationObject.put("translation_status", jsonDataObject.getString("translation_status"));
			jsonTranslationObject.put("document_original_author", jsonDataObject.getString("translation_original_author"));
			jsonTranslationObject.put("document_modification_author", jsonDataObject.getString("translation_modification_author"));
			jsonTranslationObject.put("document_date", jsonDataObject.getString("node_created_date"));
			jsonTranslationObject.put("document_name", jsonDataObject.getString("node_name"));
			jsonTranslationObject.put("document_folder", jsonDataObject.getString("parent_node_path"));
			//jsonTranslationObject.put("document_schema_name", jsonDataObject.getString("document_original_author"));
		}
		
		//
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
		jsonObject.put(AdapterConstants.ADAPTER_DATA, jsonTranslationObject);

		return jsonObject;
	}
	
	//
	public JSONObject deleteDocument(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		JSONObject jsonObject = new JSONObject();
		
		//
		// Validate that all required params are present
		if(jsonData.has("content_management_folder") == false)
		{
			throw new Exception("The 'content_management_folder' parameter is required.");
		}
		
		if(jsonData.has("document_name") == false)
		{
			throw new Exception("The 'document_name' parameter is required.");
		}
		
		//
		// Submit to the Content Management Service
		JSONObject jsonCMObject = new JSONObject();
		jsonCMObject.put("node_path", jsonData.getString("content_management_folder") + "/" + jsonData.getString("document_name"));

		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "delete_content");
		jsonMessageObject.put("service_api_data", jsonCMObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);
		JSONObject jsonResponse = processServiceResponse(serviceResponse);
		
		//
		// Process response
		JSONObject jsonTranslationObject = new JSONObject();

		if(jsonResponse.getString(AdapterConstants.ADAPTER_STATUS).equals(AdapterConstants.status.SUCCESS.toString()))
		{
			jsonTranslationObject.put("document_name", jsonData.getString("document_name"));
			jsonTranslationObject.put("document_folder", jsonData.getString("content_management_folder"));
		}
		
		//
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
		jsonObject.put(AdapterConstants.ADAPTER_DATA, jsonTranslationObject);

		return jsonObject;
	}
	
	//
	public JSONObject getDocuments(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		//
		// Validate that all required params are present
		if(jsonData.has("user_information") == false)
		{
			throw new Exception("The 'user_information' object is required.");
		}
		
		//
		// Set Properties to Search against
		JSONArray jsonProperties = new JSONArray();
		
		JSONObject jsonPropObject = new JSONObject();
		jsonPropObject.put(JackRabbitPropertyOperator.PROPERTY_OPERATOR, JackRabbitPropertyOperator.operator.AND);		
		jsonPropObject.put(TranslationStatus.TRANSLATION_STATUS, TranslationStatus.status.NEW_TRANSLATION);
		jsonProperties.put(jsonPropObject);
		
		jsonPropObject = new JSONObject();
		jsonPropObject.put(JackRabbitPropertyOperator.PROPERTY_OPERATOR, JackRabbitPropertyOperator.operator.OR);
		jsonPropObject.put(TranslationStatus.TRANSLATION_STATUS, TranslationStatus.status.UPDATED_TRANSLATION);
		jsonProperties.put(jsonPropObject);
		
		jsonPropObject = new JSONObject();
		jsonPropObject.put(JackRabbitPropertyOperator.PROPERTY_OPERATOR, JackRabbitPropertyOperator.operator.AND);
		jsonPropObject.put("jcr:mimeType", documentMimetype);
		jsonProperties.put(jsonPropObject);
		
		//
		// Build CM Object
		JSONObject jsonSearchObject = new JSONObject();
		jsonSearchObject.put("node_path", "/" + jsonData.getJSONObject("user_information").getString("user_id"));
		jsonSearchObject.put("node_properties", jsonProperties);
		
		//
		// Submit to the Content Management Service
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "search_properties");
		jsonMessageObject.put("service_api_data", jsonSearchObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);
		JSONObject jsonResponse = processServiceResponse(serviceResponse);

		//
		// Process response
		JSONArray jsonTerminologiesObject = new JSONArray();
		
		if(jsonResponse.getString(AdapterConstants.ADAPTER_STATUS).equals(AdapterConstants.status.SUCCESS.toString()))
		{
			JSONArray jsonDataArray = jsonResponse.getJSONArray(AdapterConstants.ADAPTER_DATA);
			
			for(int iIndex = 0; iIndex < jsonDataArray.length(); iIndex++)
			{
				JSONObject jsonDataObject = jsonDataArray.getJSONObject(iIndex);
				
				JSONObject jsonTermObject = new JSONObject();
				jsonTermObject.put("document_status", jsonDataObject.getString("translation_status"));
				jsonTermObject.put("document_original_author", jsonDataObject.getString("translation_original_author"));
				jsonTermObject.put("document_date", jsonDataObject.getString("node_created_date"));
				jsonTermObject.put("document_name", jsonDataObject.getString("node_name"));
				jsonTermObject.put("document_folder", jsonDataObject.getString("parent_node_path"));
				
				jsonTerminologiesObject.put(jsonTermObject);
			}
		}
		
		//
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
		jsonObject.put(AdapterConstants.ADAPTER_DATA, jsonTerminologiesObject);

		return jsonObject;
	}
	
	public JSONObject getDocument(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		//
		// Validate that all required params are present
		if(jsonData.has("content_management_folder") == false)
		{
			throw new Exception("The 'content_management_folder' parameter is required.");
		}
		
		if(jsonData.has("user_information") == false)
		{
			throw new Exception("The 'user_information' object is required.");
		}
  		
		if(jsonData.has("document_name") == false)
		{
			throw new Exception("The 'document_name' object is required.");
		}
		
		//
		// Build CM Object
		JSONObject jsonFileObject = new JSONObject();
		jsonFileObject.put("node_path", jsonData.getString("content_management_folder") + "/" + jsonData.getString("document_name"));
		
		//
		// Submit to the Content Management Service
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "get_content");
		jsonMessageObject.put("service_api_data", jsonFileObject);
		
		//
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);
		JSONObject jsonResponse = processServiceResponse(serviceResponse);
		
		//
		// Process response
		JSONObject jsonTranslationObject = new JSONObject();
		
		if(jsonResponse.getString(AdapterConstants.ADAPTER_STATUS).equals(AdapterConstants.status.SUCCESS.toString()))
		{
 		JSONObject jsonDataObject = jsonResponse.getJSONObject(AdapterConstants.ADAPTER_DATA);
			
 		//
 		TranslationParser translationParser = new TranslationParser();
 		translationParser.parse(saxBuilder, domOutputter, sourceDocumentTransform, jsonDataObject);
	 	
	 	jsonTranslationObject = translationParser.toJSON();
		}
		
		//
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
		jsonObject.put(AdapterConstants.ADAPTER_DATA, jsonTranslationObject);
		
		return jsonObject;
	}
	
	public JSONObject getDocumentProperties(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		JSONObject jsonObject = new JSONObject();
	
		//
		// Validate that all required params are present
		if(jsonData.has("content_management_folder") == false)
		{
			throw new Exception("The 'content_management_folder' parameter is required.");
		}
		
		if(jsonData.has("document_name") == false)
		{
			throw new Exception("The 'document_name' parameter is required.");
		}
		
		//
		// Build CM Object
		JSONObject jsonFileObject = new JSONObject();
		jsonFileObject.put("node_path", jsonData.getString("content_management_folder") + "/" + jsonData.getString("document_name"));
		
		//
		// Submit to the Content Management Service
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "get_content_properties");
		jsonMessageObject.put("service_api_data", jsonFileObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);
  JSONObject jsonResponse = new JSONObject(serviceResponse);
  
  //
  // Process response
  if(jsonResponse.has(ServiceConstants.SERVICE_STATUS) && jsonResponse.getString(AdapterConstants.ADAPTER_DATA).equals(AdapterConstants.status.SUCCESS))
  {
 		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
 		jsonObject.put(AdapterConstants.ADAPTER_DATA, jsonResponse.getJSONObject(AdapterConstants.ADAPTER_DATA));
  }
  else
  {
  	jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
  }

  //
		return jsonObject;
	}
		
	public JSONObject submitDocument(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		JSONObject jsonObject = new JSONObject();
		
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.UNSUPPORTED);
		return jsonObject;
	}

	//
	public JSONObject publishDocument(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		JSONObject jsonObject = new JSONObject();
		
		//
		// Validate that all required params are present
		if(jsonData.has("document_name") == false)
		{
			throw new Exception("The document_name parameter is required.");
		}
		
		if(jsonData.has("dictionary_name") == false)
		{
			throw new Exception("The dictionary_name parameter is required.");
		}
		
		//
		// Get Dictionary ID from the Datawarehouse Service
		long dictionaryID = getDictionary(identificationKey, jsonData.getString("dictionary_name"));
		
		//
		// Get Content from the CM Service
		JSONObject jsonCMObject = new JSONObject();
		jsonCMObject.put("node_path", jsonData.getString("document_name"));

		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_cm_service");
		jsonMessageObject.put("service_action", "load_document");
		jsonMessageObject.put("service_api_data", jsonCMObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);		
		JSONObject jsonResponseObject = new JSONObject(serviceResponse);
		
		//
		// Check that CM Service status is OK
		if(jsonResponseObject.has("service_status") && jsonResponseObject.getString("service_status").equals("SUCCESS"))
		{
			JSONArray jsonDocumentArray = new JSONArray();
			
			//
			JSONArray jsonArray = jsonResponseObject.getJSONArray("loaded_documents");
			
			for(int iIndex = 0; iIndex < jsonArray.length(); iIndex++)
			{
				JSONObject jsonTranslationObject = jsonArray.getJSONObject(iIndex);
				
				//
				String documentSchemaData = jsonTranslationObject.getString("document_supporting_schema_data");
				String documentStatus = jsonTranslationObject.getString("translation_status");
				String documentAuthor = jsonTranslationObject.getString("translation_original_author");
				
				//
				// Validate that Translation Document is correct (based on the schema provided with it)
				boolean documentValid = false;
				
				try
				{
					validateTranslation(jsonTranslationObject.getString("node_data"));
					documentValid = true;
				}
				catch(Exception exception)
				{
					System.out.println(exception.toString());
				}
				
				//
				// Translation is Valid, add to Document list
				if(documentValid)
				{
					//
					// Get Schema ID from the Datawarehouse Service
					long schemaID = getSchema(identificationKey, jsonTranslationObject.getString("document_supporting_schema_name"));
					
					// This Schema has not yet been added to the DW
					if(schemaID == 0L)
					{
						
					}
					
					//
					// Build DW object
					JSONObject jsonDocumentObject = new JSONObject();
					
					jsonDocumentObject.put("dictionary_id", dictionaryID);
					jsonDocumentObject.put("schema_id", schemaID);
					jsonDocumentObject.put("document_name", jsonTranslationObject.getString("node_name"));
					jsonDocumentObject.put("document_content", jsonTranslationObject.getString("node_data"));
					jsonDocumentObject.put("document_indexed", false);
					jsonDocumentObject.put("document_locked", false);
					
					jsonDocumentArray.put(jsonDocumentObject);
				}
			}
			
			//
			// Send over to the DW Service.. if all the conditions have been met
			if(jsonDocumentArray.length() > 0)
			{
				JSONObject jsonDWObject = new JSONObject();
				jsonDWObject.put("documents", jsonDocumentArray);
	
				jsonMessageObject = new JSONObject();
				jsonMessageObject.put("service_meta", "fuzein_datawarehouse_service");
				jsonMessageObject.put("service_action", "add_document");
				jsonMessageObject.put("service_api_data", jsonDWObject);
				
				serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);		
				jsonResponseObject = new JSONObject(serviceResponse);
			}
			else
			{
				System.out.println("BAD BAD BAD BAD BAD BAD BAD BAD BAD BAD BAD BAD BAD ");
			}
			
			System.out.println("=====> " + jsonResponseObject.toString());
		}
		
		//
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.SUCCESS);
		return jsonObject;
	}
	
	//
	public JSONObject searchDocuments(final String identificationKey, final JSONObject jsonData) throws Exception
	{
		JSONObject jsonObject = new JSONObject();
		
		jsonObject.put(AdapterConstants.ADAPTER_STATUS, AdapterConstants.status.UNSUPPORTED);
		return jsonObject;
	}

	//
	private void parseAdapterConfiguration(String adapterConfigurationFile) throws Exception
	{
		//
		// Parse Configuration
		Document configurationDocument = saxBuilder.build(adapterConfigurationFile);
		
		//
		// Get Translator FuzeIn Mimetype
		XPath xPath = XPath.newInstance("translator_configuration/translator_mimetype");
		Element element = (Element) xPath.selectSingleNode(configurationDocument);
		documentMimetype = element.getText();
		
		// Get Source Document Transform
		xPath = XPath.newInstance("translator_configuration/source_document_transform");
		element = (Element) xPath.selectSingleNode(configurationDocument);
		sourceDocumentTransform = element.getText();
		
		// Get Named Entities Transforms
		xPath = XPath.newInstance("translator_configuration/named_entities_transform");
		element = (Element) xPath.selectSingleNode(configurationDocument);
		namedEntitiesTransform = element.getText();
	}
	
	//
	private boolean validateTranslation(String documentDocument) throws Exception
	{
		ByteArrayInputStream objBAInputStream = new java.io.ByteArrayInputStream(documentDocument.getBytes("UTF-8"));
		documentValidator.parse(new InputSource(objBAInputStream));
		objBAInputStream.close();
		
		//
		return true;
	}
	
	private long getDictionary(String identificationKey, String dictionaryName) throws Exception
	{
		JSONObject jsonDictionaryObject = new JSONObject();
		jsonDictionaryObject.put("dictionary_name", dictionaryName);
		
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_datawarehouse_service");
		jsonMessageObject.put("service_action", "get_dictionary");
		jsonMessageObject.put("service_api_data", jsonDictionaryObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);		
		JSONObject jsonResponseObject = new JSONObject(serviceResponse);
		
		if(jsonResponseObject.has("service_status") && jsonResponseObject.getString("service_status").equals("SUCCESS"))
		{
			return jsonResponseObject.getJSONArray("dictionary_listing").getJSONObject(0).getLong("dw_dictionary_id");
 	}	
		
		return 0L;
	}
	
	private long getSchema(String identificationKey, String schemaName) throws Exception
	{
		JSONObject jsonDictionaryObject = new JSONObject();
		jsonDictionaryObject.put("schema_name", schemaName);
		
		JSONObject jsonMessageObject = new JSONObject();
		jsonMessageObject.put("service_meta", "fuzein_datawarehouse_service");
		jsonMessageObject.put("service_action", "get_schema");
		jsonMessageObject.put("service_api_data", jsonDictionaryObject);
		
		String serviceResponse = fuzeInCommunication.callService(identificationKey, jsonMessageObject);		
		JSONObject jsonResponseObject = new JSONObject(serviceResponse);
		
		if(jsonResponseObject.has("service_status") && jsonResponseObject.getString("service_status").equals("SUCCESS"))
		{
			return jsonResponseObject.getJSONArray("schema_listing").getJSONObject(0).getLong("dw_schema_id");
 	}	
		
		return 0L;
	}
}
