/*
 * Copyright (c) 2017-2018 Evolveum
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
package com.evolveum.midpoint.client.impl.restjaxb.service;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.evolveum.midpoint.xml.ns._public.common.api_types_3.*;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.midpoint.xml.ns._public.model.scripting_3.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.evolveum.midpoint.client.impl.restjaxb.RestJaxbServiceUtil;
import com.evolveum.midpoint.client.impl.restjaxb.SchemaConstants;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;
import com.evolveum.prism.xml.ns._public.types_3.ItemDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.ModificationTypeType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 *
 * @author katkav
 *
 */
@Produces({"application/xml"})
public class MidpointMockRestService {

	private  Map<String, Map<String, ? extends ObjectType>> objectMap = new HashMap<>();

	private Map<String, UserType> userMap = new HashMap<>();
	private Map<String, ValuePolicyType> valuePolicyMap = new HashMap<>();
	private Map<String, SecurityPolicyType> securityPolicyMap = new HashMap<>();

//	private RestJaxbServiceUtil util = new RestJaxbServiceUtil();
	private static final String IMPERSONATE_OID = "44af349b-5a0c-4f3a-9fe9-2f64d9390ed3";

	private static final String RESPONSE_DIR = "src/test/resources/response";

	private ValuePolicyType systemValuePolicy;
	private RestJaxbServiceUtil util;
	private JAXBContext jaxbCtx;

    private List<String> lastIncludes;
    private List<String> lastExcludes;
    private List<String> lastOptions;

	public MidpointMockRestService(JAXBContext jaxbCtx) {
		this.jaxbCtx = jaxbCtx;
		util = new RestJaxbServiceUtil(jaxbCtx);
		UserType impersonate = new UserType();
		impersonate.setName(util.createPoly("impersonate"));
		impersonate.setOid(IMPERSONATE_OID);

		userMap.put(IMPERSONATE_OID, impersonate);

		UserType jackuser = new UserType();

		jackuser.setGivenName(util.createPoly("jack"));
		jackuser.setName(util.createPoly("jack"));
		jackuser.setFamilyName(util.createPoly("jack"));
		userMap.put("876", jackuser);

		UserType jimuser = new UserType();
		jimuser.setNickName(util.createPoly("jim"));
		userMap.put("1bae776f-4939-4071-92e2-8efd5bd57799", jimuser);

		objectMap.put("users", userMap);

		valuePolicyMap.put("00000000-0000-0000-0000-000000000003", new ValuePolicyType());

		ValuePolicyType valuePolicy1 = new ValuePolicyType();
		StringPolicyType stringPolicyType = new StringPolicyType();
		LimitationsType limitations = new LimitationsType();
		limitations.setMaxLength(6);
		limitations.setMinUniqueChars(3);

		limitations.getLimit().add(createStringLimitType("abcdefghijklmnopqrstuvwxyz", null, 1, null));

		stringPolicyType.setLimitations(limitations);
		valuePolicy1.setStringPolicy(stringPolicyType);
		valuePolicyMap.put("00000000-0000-0000-0000-p00000000001", valuePolicy1);
		objectMap.put("valuePolicies", valuePolicyMap);

		systemValuePolicy = new ValuePolicyType();
		stringPolicyType = new StringPolicyType();
		limitations = new LimitationsType();
		limitations.setMaxLength(6);
		limitations.setMinUniqueChars(3);

		limitations.getLimit().add(createStringLimitType("abcdefghijklmnopqrstuvwxyz", Boolean.TRUE, 1, null));
		limitations.getLimit().add(createStringLimitType("1234567890", null, 1, null));
		limitations.getLimit().add(createStringLimitType("!@#$%^&*()_", null, 1, null));

		stringPolicyType.setLimitations(limitations);
		systemValuePolicy.setStringPolicy(stringPolicyType);

		securityPolicyMap.put("westernu-0002-0000-0000-000000000001", new SecurityPolicyType());
		objectMap.put("securityPolicies", securityPolicyMap);
	}

	private StringLimitType createStringLimitType(String allowedChars, Boolean mustBeFirst, Integer minOccurs, Integer maxOccurs){
		StringLimitType stringLimitType = new StringLimitType();
        CharacterClassOrRefType characterClasss = new CharacterClassOrRefType();
		characterClasss.setValue(allowedChars);
		stringLimitType.setCharacterClass(characterClasss);
		stringLimitType.setMustBeFirst(mustBeFirst);
		stringLimitType.setMinOccurs(minOccurs);
		stringLimitType.setMaxOccurs(maxOccurs);
		return stringLimitType;
	}

	@POST
	@Path("/{type}")
	@Consumes({MediaType.APPLICATION_XML})
	public <O extends ObjectType> Response addObject(@PathParam("type") String type, O object,
			@QueryParam("options") List<String> options,
			@Context UriInfo uriInfo, @Context MessageContext mc) {
        captureOptions(options);
        String oid = object.getOid();
		if (object.getOid() == null) {
			oid = RandomStringUtils.random(5);
			object.setOid(oid);
		}

		if ("users".equals(type)) {
			userMap.put(oid, (UserType) object);
		}

		URI location = uriInfo.getAbsolutePathBuilder().path(oid).build(oid);

		return location == null ? Response.status(Status.ACCEPTED).build() : Response.status(Status.ACCEPTED).location(location).build();
	}

	@GET
	@Path("/{type}/{id}")
	@Produces({MediaType.APPLICATION_XML})
	public <T extends ObjectType> Response getObject(@PathParam("type") String type, @PathParam("id") String id,
			@QueryParam("options") List<String> options,
			@QueryParam("include") List<String> include,
			@QueryParam("exclude") List<String> exclude,
			@Context MessageContext mc){
        captureOptions(options, include, exclude);
		OperationResultType result = new OperationResultType();
		result.setOperation("Get object");
		T objectType = (T) objectMap.get(type).get(id);

		if (objectType == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("User with oid " + id + " not found");
			return RestMockServiceUtil.createResponse(Status.NOT_FOUND, result);
		}

		return Response.status(Status.OK).header("Content-Type", MediaType.APPLICATION_XML).entity(objectType).build();

	}

	@POST
	@Path("/valuePolicies/{id}/generate")
	@Produces({MediaType.APPLICATION_XML})
	public Response policyGenerate(@PathParam("id") String id,
	                                                PolicyItemsDefinitionType object,
	                                                 @QueryParam("options") List<String> options,
	                                                 @QueryParam("include") List<String> include,
	                                                 @QueryParam("exclude") List<String> exclude,
	                                                 @Context MessageContext mc){
        captureOptions(options, include, exclude);
        OperationResultType result = new OperationResultType();
		result.setOperation("Policy generate");

		String policyOid = object.getPolicyItemDefinition().get(0).getValuePolicyRef().getOid();
		ValuePolicyType policy = (ValuePolicyType)objectMap.get("valuePolicies").get(policyOid);

		if (policy == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("Policy with oid " + id + " not found");
			return RestMockServiceUtil.createResponse(Status.NOT_FOUND, result);
		}

		try
		{
			DocumentBuilderFactory dFact = DocumentBuilderFactory.newInstance();
			DocumentBuilder build = dFact.newDocumentBuilder();
			Document doc = build.newDocument();
			Element value = doc.createElement("value");
			Node node = doc.createTextNode("dj38dj");
			value.appendChild(node);
			object.getPolicyItemDefinition().get(0).setValue(value);

		}
		catch(ParserConfigurationException e){
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("Failure creating response");
			return RestMockServiceUtil.createResponse(Status.INTERNAL_SERVER_ERROR, result);
		}

		return Response.status(Status.OK).header("Content-Type", MediaType.APPLICATION_XML).entity(object).build();
	}

	@POST
	@Path("/rpc/generate")
	@Produces({MediaType.APPLICATION_XML})
	public Response generate(PolicyItemsDefinitionType object, @Context MessageContext mc){

		OperationResultType result = new OperationResultType();
		result.setOperation("Rpc generate");

		if (object == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("Policy items definition cannot be null");
			return RestMockServiceUtil.createResponse(Status.BAD_REQUEST, result);
		}

		generate(object, result);

		OperationResultUtil.computeStatusIfUnknown(result);
		if (OperationResultUtil.isSuccess(result)) {
			return RestMockServiceUtil.createResponse(Status.OK, object, result);
		}

		return RestMockServiceUtil.createResponse(Status.BAD_REQUEST, object, result, true);


	}

	private void generate(PolicyItemsDefinitionType object, OperationResultType result){
List<PolicyItemDefinitionType> policyItemDefinitionTypes = object.getPolicyItemDefinition();

		policyItemDefinitionTypes.forEach(itemDefinition ->
		{
			ObjectReferenceType ref = itemDefinition.getValuePolicyRef();
			ValuePolicyType valuePolicy = resolveValuePolicyType(ref);
			try {
				ValuePolicyProcessor processor = new ValuePolicyProcessor();
			Object value = processor.generate(itemDefinition.getTarget() != null ? itemDefinition.getTarget().getPath() : null, valuePolicy, result);
			itemDefinition.setValue(value);
			} catch (Exception e) {
				result.setStatus(OperationResultStatusType.FATAL_ERROR);
				result.setMessage("Cannot generate value for: " + itemDefinition);
				itemDefinition.setResult(result);
				RestMockServiceUtil.createResponse(Status.BAD_REQUEST, itemDefinition, result);
			}
		}
		);
	}



	@POST
	@Path("/rpc/validate")
	@Produces({MediaType.APPLICATION_XML})
	public Response validate(PolicyItemsDefinitionType object, @Context MessageContext mc){

		OperationResultType result = new OperationResultType();
		result.setOperation("Rpc generate");

		if (object == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("Policy items definition cannot be null");
			return RestMockServiceUtil.createResponse(Status.BAD_REQUEST, result);
		}

		List<PolicyItemDefinitionType> policyItemDefinitionTypes = object.getPolicyItemDefinition();

		policyItemDefinitionTypes.forEach(itemDefinition ->
		{
			ObjectReferenceType ref = itemDefinition.getValuePolicyRef();
			ValuePolicyType valuePolicy = resolveValuePolicyType(ref);
			try {
				ValuePolicyProcessor processor = new ValuePolicyProcessor();
				OperationResultType valueResult = new OperationResultType();
				valueResult.setOperation("valueResult");
				result.getPartialResults().add(valueResult);
			processor.validate(valuePolicy, (String) itemDefinition.getValue(), valueResult);
			OperationResultUtil.computeStatus(valueResult);
			itemDefinition.setResult(valueResult);
			} catch (Exception e) {
				result.setStatus(OperationResultStatusType.PARTIAL_ERROR);
				result.setMessage("Cannot validate value for: " + itemDefinition);
				itemDefinition.setResult(result);
				RestMockServiceUtil.createResponse(Status.BAD_REQUEST, itemDefinition, result);
			}
		}
		);

		OperationResultUtil.computeStatusIfUnknown(result);
		if (OperationResultUtil.isSuccess(result)) {
			return RestMockServiceUtil.createResponse(Status.OK, object, result, true);
		}

		return RestMockServiceUtil.createResponse(Status.BAD_REQUEST, object, result, true);


	}

	private ValuePolicyType resolveValuePolicyType(ObjectReferenceType ref) {
		if (ref != null && ref.getOid() != null) {
			return valuePolicyMap.get(ref.getOid());
		}

		return systemValuePolicy;

	}


	@POST
	@Path("/{type}/{id}/generate")
	@Produces({MediaType.APPLICATION_XML})
	public <T extends ObjectType> Response generate(@PathParam("type") String type, @PathParam("id") String id,
	                                                      PolicyItemsDefinitionType object,
	                                                      @QueryParam("options") List<String> options,
	                                                      @QueryParam("include") List<String> include,
	                                                      @QueryParam("exclude") List<String> exclude,
	                                                      @Context MessageContext mc) throws Exception {
        captureOptions(options, include, exclude);
        OperationResultType result = new OperationResultType();
		result.setOperation("Modify generate object");
		UserType user = (UserType) objectMap.get(type).get(id);

		if (user == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("User with oid " + id + " not found");
			return RestMockServiceUtil.createResponse(Status.NOT_FOUND, result);
		}


		generate(object, result);

//		String newValue = "Bob";
//
//		try
//		{
//			DocumentBuilderFactory dFact = DocumentBuilderFactory.newInstance();
//			DocumentBuilder build = dFact.newDocumentBuilder();
//			Document doc = build.newDocument();
//			Element value = doc.createElement("value");
//			Node node = doc.createTextNode(newValue);
//			value.appendChild(node);
//			object.getPolicyItemDefinition().get(0).setValue(value);
//
//		}
//		catch(ParserConfigurationException e){
//			result.setStatus(OperationResultStatusType.FATAL_ERROR);
//			result.setMessage("Failure creating response");
//			return RestMockServiceUtil.createResponse(Status.INTERNAL_SERVER_ERROR, result);
//		}

		user.setGivenName(util.createPoly((String) object.getPolicyItemDefinition().iterator().next().getValue()));

		return Response.status(Status.OK).header("Content-Type", MediaType.APPLICATION_XML).entity(object).build();
	}

	@POST
	@Path("/{type}/{id}")
	@Produces({MediaType.APPLICATION_XML})
	public <T extends ObjectType> Response modifyObjectPost(@PathParam("type") String type, @PathParam("id") String id,
	                                                        ObjectModificationType object,
	                                                        @QueryParam("options") List<String> options,
	                                                        @Context UriInfo uriInfo, @Context MessageContext mc) throws Exception {
        captureOptions(options);
        //TODO: Should we make this generic or does this satisfy our needs for the test case?

		OperationResultType result = new OperationResultType();
		result.setOperation("Modify object");

		UserType objectType = (UserType) objectMap.get(type).get(id);

		if (objectType == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("User with oid " + id + " not found");
			return RestMockServiceUtil.createResponse(Status.NOT_FOUND, result);
		}

		//Grab changes from the ObjectModificationType
		List<ItemDeltaType> deltaTypeList = object.getItemDelta();

		for(ItemDeltaType delta : deltaTypeList){
			if(delta.getModificationType() == ModificationTypeType.ADD){
				objectType.setGivenName((PolyStringType)delta.getValue().get(0));
			}
			else if(delta.getModificationType() == ModificationTypeType.REPLACE){

				objectType.setDescription(delta.getValue().get(0).toString());
			}
			else{ //ModificationTypeType.DELETE
				objectType.setGivenName(null);
			}
		}

		return Response.status(Status.NO_CONTENT).header("Content-Type", MediaType.APPLICATION_XML).entity(objectType).build();
	}

	@DELETE
	@Path("/{type}/{id}")
	@Produces({MediaType.APPLICATION_XML})
	public <T extends ObjectType> Response deleteObject(@PathParam("type") String type, @PathParam("id") String id,
                                                        @QueryParam("options") List<String> options,
                                                        @QueryParam("include") List<String> include,
                                                        @QueryParam("exclude") List<String> exclude,
                                                        @Context MessageContext mc){
        captureOptions(options, include, exclude);
        OperationResultType result = new OperationResultType();
		result.setOperation("Delete object");

        if (!objectMap.get(type).containsKey(id)) {
            result.setStatus(OperationResultStatusType.FATAL_ERROR);
            result.setMessage("Object with oid " + id + " was not found.");
            return RestMockServiceUtil.createResponse(Status.NOT_FOUND, result);
        }

		objectMap.get(type).remove(id);

		return Response.status(Status.OK).header("Content-Type", MediaType.APPLICATION_XML).build();
	}

	@POST
	@Path("/{type}/search")
	@Produces({MediaType.APPLICATION_XML})
	@Consumes({MediaType.APPLICATION_XML})
	public Response searchObjects(@PathParam("type") String type, QueryType queryType,
			@QueryParam("options") List<String> options,
			@QueryParam("include") List<String> include,
			@QueryParam("exclude") List<String> exclude,
			@Context MessageContext mc){
        captureOptions(options, include, exclude);
        OperationResultType result = new OperationResultType();
		result.setOperation("Search objects");

		ObjectListType resultList = new ObjectListType();

		try {
			Marshaller marshaller = jaxbCtx.createMarshaller();
			StringWriter writer = new StringWriter();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			marshaller.marshal(new JAXBElement<QueryType>(new QName(SchemaConstants.NS_QUERY, "query"), QueryType.class, queryType), writer);
			System.out.println("Query received on the service: " + writer);
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return RestMockServiceUtil.createResponse(Status.OK, resultList, result);
	}

	@GET
	@Path("/self")
	@Produces({MediaType.APPLICATION_XML})
	public Response self(@Context MessageContext mc) throws Exception {
		OperationResultType result = new OperationResultType();
		result.setOperation("Self");

		String impersonateOid = mc.getHttpHeaders().getHeaderString("Switch-To-Principal");
		UserType userType;

		if(null != impersonateOid){
            userType = userMap.get(impersonateOid);
            if (userType == null) {
                return RestMockServiceUtil.createResponse(Status.UNAUTHORIZED, result);
            }
		} else {
			userType = new UserType();
			userType.setName(util.createPoly("administrator"));
		}

		return RestMockServiceUtil.createResponse(Status.OK, userType, result);
	}

	@POST
	@Path("/users/{oid}/credential")
	@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, "application/yaml"})
	@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON, "application/yaml"})
	public Response executeCredentialReset(@PathParam("oid") String oid, ExecuteCredentialResetRequestType executeCredentialResetRequest, @Context MessageContext mc) {
		OperationResultType result = new OperationResultType();
		result.setOperation("credentialReset");

		UserType user = userMap.get(oid);

		if (user == null) {
			result.setStatus(OperationResultStatusType.FATAL_ERROR);
			result.setMessage("User with oid " + oid + " not found");
			return RestMockServiceUtil.createResponse(Status.NOT_FOUND, result);
		}

		// TODO
		return Response.status(Status.OK).header("Content-Type", MediaType.APPLICATION_XML).build();
	}

	@POST
	@Path("/rpc/executeScript")
	@Consumes({"application/xml", MediaType.APPLICATION_JSON, "application/yaml" })
	public Response executeScript(ExecuteScriptType command,
			@QueryParam("asynchronous") Boolean asynchronous, @Context UriInfo uriInfo, @Context MessageContext mc) {

		// we support only two predefined requests

		if (command.getScriptingExpression().getValue() instanceof ExpressionPipelineType) {
			ExpressionPipelineType pipeline = (ExpressionPipelineType) command.getScriptingExpression().getValue();
			if (pipeline.getScriptingExpression().size() >= 2) {
				ScriptingExpressionType second = pipeline.getScriptingExpression().get(1).getValue();
				if (second instanceof ActionExpressionType) {
					String type = ((ActionExpressionType) second).getType();
					if ("generate-value".equals(type)) {
						return buildResponseFromFile(new File(RESPONSE_DIR, "/response-generate-passwords.xml"));
					} else if ("modify".equals(type)) {
						return buildResponseFromFile(new File(RESPONSE_DIR, "/response-modify-validTo.xml"));
					}
				}
			}
		}
		throw new UnsupportedOperationException("Unsupported executeScript call");
	}

	private Response buildResponseFromFile(File file) {
		try {
			return Response.status(Status.OK)
					.header("Content-Type", MediaType.APPLICATION_XML)
					.entity(Files.readAllBytes(file.toPath()))
					.build();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}


    void captureOptions(List<String> options) {
        captureOptions(options, null, null);
    }

    void captureOptions(List<String> options, List<String> include, List<String> exclude) {
        lastOptions = options;
        lastIncludes = include;
        lastExcludes = exclude;
    }

    public List<String> getLastExcludes() {
        return lastExcludes;
    }

    public List<String> getLastIncludes() {
        return lastIncludes;
    }

    public List<String> getLastOptions() {
        return lastOptions;
    }
}
