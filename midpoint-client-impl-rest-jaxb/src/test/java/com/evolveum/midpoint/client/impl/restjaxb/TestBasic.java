/**
 * Copyright (c) 2017 Evolveum
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
package com.evolveum.midpoint.client.impl.restjaxb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertNotNull;

import java.io.IOException;
import java.util.Arrays;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.transport.local.LocalConduit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.evolveum.midpoint.client.api.ObjectReference;
import com.evolveum.midpoint.client.api.SearchResult;
import com.evolveum.midpoint.client.api.Service;
import com.evolveum.midpoint.client.api.exception.ObjectNotFoundException;
import com.evolveum.midpoint.client.impl.restjaxb.service.AuthenticationProvider;
import com.evolveum.midpoint.client.impl.restjaxb.service.MidpointMockRestService;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ItemPathType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * @author semancik
 *
 */
public class TestBasic {
	
	private static Server server;
	private static final String ENDPOINT_ADDRESS = "http://localhost:18080/rest";
	
	@BeforeClass
	public void init() throws IOException {
		startServer();
	}
	
	@Test
	public void testUserSearch() throws Exception {
		Service service = getService();
		
		// WHEN
		ItemPathType itemPath = new ItemPathType();
		itemPath.setValue("name");
		AssignmentType cal = new AssignmentType();
		cal.setDescription("asdasda");
		ObjectReferenceType ort = new ObjectReferenceType();
		ort.setOid("12312313");
		cal.setTargetRef(ort);
		ActivationType activation = new ActivationType();
		activation.setAdministrativeStatus(ActivationStatusType.ARCHIVED);
		cal.setActivation(activation);
		service.users().search().queryFor(service, UserType.class).item(itemPath).eq().finishQuery().build();
		SearchResult<UserType> result = service.users().search().queryFor(service, UserType.class).item(itemPath).eq("jack").finishQuery().build().get();
		
		// THEN
		assertEquals(result.size(), 1);
	}
	
	@Test
	public void testUserGet() throws Exception {
		Service service = getService();
		
		// WHEN
		UserType userType = service.users().oid("123").get();
		
		// THEN
		assertNotNull("null user", userType);
	}
	
	@Test
	public void testUserGetNotExist() throws Exception {
		Service service = getService();
		
		// WHEN
		try {
			service.users().oid("999").get();
			fail("Unexpected user found");
		} catch (ObjectNotFoundException e) {
			// nothing to do. this is expected
		}
		
	}
	
	@Test
	public void testUserAdd() throws Exception {
		Service service = getService();
		
		UserType userBefore = new UserType();
		userBefore.setName(service.util().createPoly("foo"));
		userBefore.setOid("123");
		
		// WHEN
		ObjectReference<UserType> ref = service.users().add(userBefore).post();
		
		// THEN
		assertNotNull("Null oid", ref.getOid());
		
		UserType userAfter = ref.get();
		Asserts.assertPoly(service, "Wrong name", "foo", userAfter.getName());
		
		// TODO: get user, compare
		
	}
	
	private Service getService() throws IOException {
		
		RestJaxbServiceBuilder serviceBuilder = new RestJaxbServiceBuilder();
		serviceBuilder.authentication(AuthenticationType.BASIC).username("administrator").password("5ecr3t").url(ENDPOINT_ADDRESS);
		RestJaxbService service = serviceBuilder.build();
		WebClient client = service.getClient();
		WebClient.getConfig(client).getRequestContext().put(LocalConduit.DIRECT_DISPATCH, Boolean.TRUE);
		
		return service;
		
	}
	
	private void startServer() throws IOException {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
	     sf.setResourceClasses(MidpointMockRestService.class);
	     
	     sf.setProviders(Arrays.asList(new JaxbXmlProvider<>(createJaxbContext()), new AuthenticationProvider()));
	         
	     sf.setResourceProvider(MidpointMockRestService.class,
	                            new SingletonResourceProvider(new MidpointMockRestService(), true));
	     sf.setAddress(ENDPOINT_ADDRESS);
	 
	     
	     server = sf.create();
	}
	
	private JAXBContext createJaxbContext() throws IOException {
		try {
		JAXBContext jaxbCtx = JAXBContext.newInstance("com.evolveum.midpoint.xml.ns._public.common.api_types_3:"
				+ "com.evolveum.midpoint.xml.ns._public.common.audit_3:"
				+ "com.evolveum.midpoint.xml.ns._public.common.common_3:"
				+ "com.evolveum.midpoint.xml.ns._public.connector.icf_1.connector_extension_3:"
				+ "com.evolveum.midpoint.xml.ns._public.connector.icf_1.connector_schema_3:"
				+ "com.evolveum.midpoint.xml.ns._public.connector.icf_1.resource_schema_3:"
				+ "com.evolveum.midpoint.xml.ns._public.gui.admin_1:"
				+ "com.evolveum.midpoint.xml.ns._public.model.extension_3:"
				+ "com.evolveum.midpoint.xml.ns._public.model.scripting_3:"
				+ "com.evolveum.midpoint.xml.ns._public.model.scripting.extension_3:"
				+ "com.evolveum.midpoint.xml.ns._public.report.extension_3:"
				+ "com.evolveum.midpoint.xml.ns._public.resource.capabilities_3:"
				+ "com.evolveum.midpoint.xml.ns._public.task.extension_3:"
				+ "com.evolveum.midpoint.xml.ns._public.task.jdbc_ping.handler_3:"
				+ "com.evolveum.midpoint.xml.ns._public.task.noop.handler_3:"
				+ "com.evolveum.prism.xml.ns._public.annotation_3:"
				+ "com.evolveum.prism.xml.ns._public.query_3:"
				+ "com.evolveum.prism.xml.ns._public.types_3");
		return jaxbCtx;
		} catch (JAXBException e) {
			throw new IOException(e);
		}
		
	}
	

}