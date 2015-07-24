package com.demo;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.Gmail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

public class GmailQuickstart {
	private static final String APPLICATION_NAME = "GmailSearch";

	private static final java.io.File DATA_STORE_DIR = new java.io.File(
			"d:/GmailTest",".credentials/gmail-api-quickstart");

	private static FileDataStoreFactory DATA_STORE_FACTORY;

	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	private static HttpTransport HTTP_TRANSPORT;

	private static final List<String> SCOPES = Arrays.asList(GmailScopes.GMAIL_READONLY);

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Creates an authorized Credential object.
	 * 
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	public static Credential authorize() throws IOException {
		// Load client secrets.
		InputStream in = new FileInputStream(new File("D:/MailSearch/FindMail/res/client_secret.json"));
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
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

	/**
	 * Build and return an authorized Gmail client service.
	 * 
	 * @return an authorized Gmail client service
	 * @throws IOException
	 */
	public static Gmail getGmailService() throws IOException {
		Credential credential = authorize();
		return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				.setApplicationName(APPLICATION_NAME).build();
	}

	public static void main(String[] args) throws IOException, MessagingException {
		// Build a new authorized API client service.
		Gmail service = getGmailService();

		// Print the labels in the user's account.
		String user = "me";
		
		ListMessagesResponse messages = service.users().messages().list(user).execute();
	
		if (messages.size() == 0) {
			System.out.println("No Emails found.");
		} else {
			System.out.println("Labels:");
			for (Message label : messages.getMessages()) {
				Message message = service.users().messages().get(user, label.getId()).setFormat("raw").execute();
				byte[] emailBytes = message.decodeRaw();

				Properties props = new Properties();
				Session session = Session.getDefaultInstance(props, null);

				MimeMessage email = new MimeMessage(session, new ByteArrayInputStream(emailBytes));
				
				System.out.printf("- %s\n", email.getSender());
				System.out.println(email.getContent());
				System.out.println(email.getContentID());
				System.out.println(email.getContentType());
				System.out.println(email.getDescription());
				System.out.println(email.getEncoding());
				System.out.println(email.getFileName());
				System.out.println(email.getSubject());
				System.out.println(email.getAllRecipients());
				System.out.println(email.getReceivedDate());
				System.out.println(email.getSender());
				System.out.println(email.getSentDate());
				
			}
		}
	}

}