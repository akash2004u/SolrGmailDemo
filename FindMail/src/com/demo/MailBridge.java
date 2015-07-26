package com.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

public class MailBridge {

	private static final String SolrServerAddress = "http://localhost:8983/solr/heapwalk";
	private static final String APPLICATION_NAME = "GmailSearch";
	private static final java.io.File DATA_STORE_DIR = new java.io.File(
			System.getProperty("user.home"),
			".credentials/gmail-api-quickstart");
	private static FileDataStoreFactory DATA_STORE_FACTORY;
	private static final JsonFactory JSON_FACTORY = JacksonFactory
			.getDefaultInstance();
	private static HttpTransport HTTP_TRANSPORT;
	private static final List<String> SCOPES = Arrays
			.asList(GmailScopes.GMAIL_READONLY);

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	public static Credential authorize() throws IOException {
		InputStream in = new FileInputStream(new File("res/client_secret.json"));
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY, new InputStreamReader(in));

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(DATA_STORE_FACTORY)
				.setAccessType("offline").build();

		Credential credential = new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");

		System.out.println("Credentials saved to "
				+ DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	public static Gmail getGmailService() throws IOException {
		Credential credential = authorize();
		return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName(APPLICATION_NAME).build();
	}

	public static void main(String[] args) throws IOException,
			MessagingException, SolrServerException {
		// Build a new authorized API client service.
		Gmail service = getGmailService();
		String user = "me";
		ListMessagesResponse messages = service.users().messages().list(user)
				.execute();
		
		for(int i = 0; i < 6;i++)
	     messages = service.users().messages().list(user).setPageToken(messages.getNextPageToken()).execute();
	     
	    int cnt = 4;
		 while (messages.getMessages() != null) { 
			 System.out.println("Loading Page  *************************************************"+(cnt++));
				if (messages.size() == 0) {
					System.out.println("No Emails found.");
				} else {
					System.out.println("Labels:");
					
					for (Message label : messages.getMessages()) {
						Message message = service.users().messages()
								.get(user, label.getId()).setFormat("raw").execute();
						byte[] emailBytes = message.decodeRaw();

						Properties props = new Properties();
						Session session = Session.getDefaultInstance(props, null);

						MimeMessage email = new MimeMessage(session,new ByteArrayInputStream(emailBytes));
						indexEmail(label.getId(), email);
					}
				}
			 
		      if (messages.getNextPageToken() != null) {
		        String pageToken = messages.getNextPageToken();
		        messages = service.users().messages().list(user)
		            .setPageToken(pageToken).execute();
		      } else {
		        break;
		      }
		    }

	   	
	}

	public static void addToIndex(String id, String subject,
			List<String> sentTo, String author, String body,
			List<String> attachments,Date sentOn) throws SolrServerException, IOException {


		System.out.println("New Message");
		System.out.println("Addging Doc");
		System.out.println("Id :"+id);
		System.out.println("To :"+sentTo);
		System.out.println("author :"+author);
		System.out.println("body :"+body);
		System.out.println("attachments :"+attachments);
		System.out.println("(*****");
				
		HttpSolrServer server = new HttpSolrServer(SolrServerAddress);
		SolrInputDocument doc = new SolrInputDocument();

		
		doc.addField("id", id);
		doc.addField("subject", subject);

		for (String toVal : sentTo) {
			doc.addField("to", toVal);
		}

		doc.addField("from", author);
		doc.addField("body", body.replaceAll("(\\r)", ""));
		for (String attachment : attachments) {
			doc.addField("attachment", attachment);
		}
		doc.addField("sentOn", sentOn);

		server.add(doc);
		server.commit();
	}

	public static void indexEmail(String id, MimeMessage email)
			throws MessagingException, IOException, SolrServerException {

		String subject = email.getSubject();
		List<String> toList = new ArrayList<String>();
		for (Address add : email.getAllRecipients()) {
			toList.add(add.toString());
		}
		String author = email.getFrom() == null ? "" : email.getFrom()[0]
				.toString();
		Date date = email.getSentDate();

		MimeMessage message = email;
		String result = "";
		List<String> attachments = new ArrayList<String>();
		
		if (message instanceof MimeMessage) { 
			Object contentObject = email.getContent();
			if (contentObject instanceof Multipart) {
				BodyPart clearTextPart = null;
				BodyPart htmlTextPart = null;
				Multipart contentPart = (Multipart) contentObject;
				int count = contentPart.getCount();
				for (int i = 0; i < count; i++) {
					BodyPart part = contentPart.getBodyPart(i);

					String disposition = part.getDisposition();
					
					if (disposition != null && (disposition.equalsIgnoreCase("ATTACHMENT"))) {
							System.out.println("Mail have some attachment");
							DataHandler handler = part.getDataHandler();
							System.out.println("file name : " + handler.getName()); 
							attachments.add(handler.getName());
					} else if (part.isMimeType("text/plain")) {
						clearTextPart = part;
						break;
					} else if (part.isMimeType("text/html")) {
						htmlTextPart = part;
					}
				}

				if (clearTextPart != null) {
					result = (String) clearTextPart.getContent();
				} else if (htmlTextPart != null) {
					String html = (String) htmlTextPart.getContent();
					result = Jsoup.parse(html).text();
				}

			} else if (contentObject instanceof String) {
				result = (String) contentObject;
			} else {
				result = "";
			}

		}
		

		addToIndex(id,subject,toList, author, result,attachments,date);

		/*
		 * 
		 * 
		 * Object msgContent = messages[i].getContent();
		 * 
		 * String content = "";
		 * 
		 * if (msgContent instanceof Multipart) {
		 * 
		 * Multipart multipart = (Multipart) msgContent;
		 * 
		 * Log.e("BodyPart", "MultiPartCount: "+multipart.getCount());
		 * 
		 * for (int j = 0; j < multipart.getCount(); j++) {
		 * 
		 * BodyPart bodyPart = multipart.getBodyPart(j);
		 * 
		 * 
		 * 
		 */

		System.out.println("Resul :" + result);

	}
}
