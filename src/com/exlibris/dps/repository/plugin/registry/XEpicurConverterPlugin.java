package com.exlibris.dps.repository.plugin.registry;

/*
 * Created on 19/02/2006
 *
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nbnDe11112004033116.EpicurDocument;
import nbnDe11112004033116.FormatType;
import nbnDe11112004033116.IdentifierType;
import nbnDe11112004033116.IdentifierType.Scheme;
import nbnDe11112004033116.RecordType;
import nbnDe11112004033116.ResourceType;
import nbnDe11112004033116.UpdateStatusType;
import nbnDe11112004033116.UpdateStatusType.Type.Enum;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;

import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper.ObjectIdentifier;
import com.exlibris.dps.sdk.deposit.IEParser;
import com.exlibris.dps.sdk.deposit.IEParserFactory;
import com.exlibris.dps.sdk.registry.ConverterRegistryPlugin;
import com.exlibris.dps.services.PropertiesService;

public class XEpicurConverterPlugin implements ConverterRegistryPlugin {

	private String defaultMimeType = "text/html";
	boolean createViewerUrl = false;
	private String statusType;
	boolean toUpdate = false;
	private static final String TEMPLATE_FILE = "conf/xepicur_template.xml";
	private static String DELIVERY_PATH = "delivery/DeliveryManagerServlet?dps_custom_att_1=xepicur&dps_pid=";
	private String deliveryServer = null;
	private String recordStatus = null;
	private String pid;

	 public String unPublish(String ieXml) {

	 Map<String, String> map = new HashMap<String, String>();
	 map.put("status_type", "url_delete");
	 initParam(map);
	 String existEpicurStr = convert(ieXml);

	 return existEpicurStr;
	 }

	public String convert(String ieXml) {

		// read from template
		EpicurDocument epDoc = null;
		IEParser parser = null;
		try {
			parser = IEParserFactory.parse(ieXml);
		} catch (XmlException e2) {
			System.err.println("Unable to parse ieXml");
		}
		String urn = getUrnFromDNX(ieXml);

		InputStream is = this.getClass().getClassLoader().getResourceAsStream(TEMPLATE_FILE);
		StringWriter writer = new StringWriter();
		String templateFullFile = null;
		try {
			IOUtils.copy(is, writer,  "UTF-8");
			templateFullFile = writer.toString();
		} catch (IOException e1) {
			System.err.println("Unable to read the "+TEMPLATE_FILE);
		}

		if (templateFullFile != null) {
			try {
				epDoc = EpicurDocument.Factory.parse(templateFullFile);
			} catch (XmlException e) {
				e.printStackTrace();
			}
		} else {
			epDoc = EpicurDocument.Factory.newInstance();
		}

		RecordType record = validateEpicur(epDoc) ;

		// fill epicur
		if (record.getIdentifier().getScheme() == null) {
			record.getIdentifier().setScheme(Scheme.URN_NBN_DE);
		}

		if(urn == null) {
			System.out.println("The IE PID " + pid +" is not published by Xepicur converter because URN is not found.");
			//logger.warn("The IE PID " + pid +" is not published by Xepicur converter because URN is not found.", pid);
			return null;
		}
		if("UPDATE".equals(recordStatus)) {
			System.out.println("The IE PID " + pid +" is not re-published by Xepicur converter because the ststus is update.");
			//logger.warn("The IE PID " + pid +" is not re-published by Xepicur converter because the ststus is update.", pid);
			return null;
		}
		record.getIdentifier().setStringValue(urn);
		insertURLs(record);

		// set status
		UpdateStatusType ust = UpdateStatusType.Factory.newInstance();
		switch (whichType(statusType)) {
		case 0:
			ust.setType(UpdateStatusType.Type.URL_INSERT);
			break;
		case 1:
			ust.setType(UpdateStatusType.Type.URL_UPDATE_GENERAL);
			break;
		case 2:
			ust.setType(UpdateStatusType.Type.URN_NEW);
			break;
		case 3:
			ust.setType(UpdateStatusType.Type.URL_DELETE);
			break;
		default:
			ust.setType(UpdateStatusType.Type.URN_NEW);
		}

		epDoc.getEpicur().getAdministrativeData().getDelivery().setUpdateStatus(ust);

		return epDoc.toString();
	}



	private int whichType(String statusType) {
		 Enum enumStatus = Enum.forString(statusType);
		if (UpdateStatusType.Type.URL_INSERT.equals(enumStatus)) {
			return 0;
		}
		if (UpdateStatusType.Type.URL_UPDATE_GENERAL.equals(enumStatus)) {
			return 1;
		}
		if (UpdateStatusType.Type.URN_NEW.equals(enumStatus)) {
			return 2;
		}
		if (UpdateStatusType.Type.URL_DELETE.equals(enumStatus)) {
			return 3;
		}
		return 2;
	}

	/**
	 * @param digitalEntity
	 * @param record
	 */
	private void insertURLs(RecordType record) {

		String mimeType = null;
		mimeType = defaultMimeType;
		String url = getDeliveryServerUrl() + DELIVERY_PATH + pid;
		ResourceType resourceType = record.addNewResource();
		IdentifierType identType = resourceType.addNewIdentifier();
		FormatType formatType = resourceType.addNewFormat();
		formatType.setScheme(FormatType.Scheme.Enum
				.forInt(FormatType.Scheme.INT_IMT));
		formatType.setStringValue(mimeType);
		identType.setStringValue(url);
		identType.setScheme(IdentifierType.Scheme.URL);
		identType.setRole(IdentifierType.Role.PRIMARY);
	}

	/**
	 * @param epDoc
	 */
	private RecordType validateEpicur(EpicurDocument epDoc) {
		if (epDoc.getEpicur() == null) {
			epDoc.addNewEpicur();
		}
		if (epDoc.getEpicur().getAdministrativeData() == null) {
			epDoc.getEpicur().addNewAdministrativeData();
		}
		if (epDoc.getEpicur().getAdministrativeData().getDelivery() == null) {
			epDoc.getEpicur().getAdministrativeData().addNewDelivery();
		}
		if (epDoc.getEpicur().getAdministrativeData().getDelivery()
				.getTransfer() == null) {
			epDoc.getEpicur().getAdministrativeData().getDelivery()
					.addNewTransfer();
		}
		if (epDoc.getEpicur().getAdministrativeData().getDelivery()
				.getUpdateStatus() == null) {
			epDoc.getEpicur().getAdministrativeData().getDelivery()
					.addNewUpdateStatus();
		}
		RecordType record;
		if (epDoc.getEpicur().getRecordArray().length == 0) {
			record = epDoc.getEpicur().addNewRecord();
		} else {
			record = epDoc.getEpicur().getRecordArray(0);
		}
		if (record.getIdentifier() == null) {
			record.addNewIdentifier();
		}

		return record;
	}

	public void initParam(Map<String, String> params) {
		String status = (String) params.get("status_type");
		String pRecordStatus = (String) params.get("record_status");
		String pid = (String) params.get("PID");
		recordStatus = "NEW";
		if (status != null) {
			statusType = status;
		}
		if (pRecordStatus != null) {
			recordStatus  = pRecordStatus;
		}
		if (pid != null) {
			this.pid  = pid;
		}
	}

	private String getUrnFromDNX(String ieXml) {
		try {
			IEParser parser = IEParserFactory.parse(ieXml);

			DnxDocumentHelper helper = new DnxDocumentHelper(parser.getIeDnx());
			List<ObjectIdentifier> list = helper.getObjectIdentifiers();
			for (ObjectIdentifier identifier : list) {
				if (identifier.getObjectIdentifierType().equals("URN")) {
					return identifier.getObjectIdentifierValue();
				}
			}
		} catch (Exception e) {
			//logger.warn("Failed to extract URN from METS");
		}

		return null;
	}
	private StringBuffer getDeliveryServerUrl(){
		StringBuffer deliveryUrl = new StringBuffer();

		PropertiesService propertiesService = new PropertiesService();
		deliveryServer = propertiesService.get(propertiesService.DELIVERY_SERVER);
		return deliveryUrl.append(deliveryServer);
	}

//		StringBuffer deliveryUrl = new StringBuffer();
//		// set deliveryServer
//		if(deliveryServer == null){
//			try {
//				GeneralParameterManager gpm =  ServiceLocator.getInstance().lookUp(GeneralParameterManager.class);
//				deliveryServer = gpm.getParameter("delivery", "delivery_server");
//			} catch (Exception e) {
//				System.err.println("Failed to build redirect URL");
//				e.printStackTrace();
//			}
//		}
//
//		return deliveryUrl.append(deliveryServer);
//	}

	public static void main(String[] args) {

		StringWriter writer = new StringWriter();
		InputStream in = null;
		try {
			File example = new File(XEpicurConverterPlugin.class.getClassLoader().getResource("").getPath()+"/conf/IE_example.xml");
			in = new FileInputStream(example);
			IOUtils.copy(in, writer,  "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (in!=null)
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}

		XEpicurConverterPlugin my = new XEpicurConverterPlugin();
		Map<String, String> params = new HashMap<String, String>();
		params.put("PID", "IE1000");
		my.initParam(params);

		System.out.print(my.convert(writer.toString()));
	}
}
