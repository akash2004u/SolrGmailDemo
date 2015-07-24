package com.demo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
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

	private static final String SolrServerAddress = "";
	private static final String APPLICATION_NAME = "GmailSearch";
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),".credentials/gmail-api-quickstart");
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

	public static Credential authorize() throws IOException {
		InputStream in = new FileInputStream(new File("res/client_secret.json"));
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(DATA_STORE_FACTORY)
				.setAccessType("offline").build();
		
		Credential credential = new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");
		
		System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	public static Gmail getGmailService() throws IOException {
		Credential credential = authorize();
		return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	}

	public static void main(String[] args) throws IOException, MessagingException {
		// Build a new authorized API client service.
		Gmail service = getGmailService();
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
				indexEmail(label.getId(),email);
			}
		}
	}
	
	public static void addToIndex(String contentId,String id,String subject,String cat,String author,String body,String[] attachments) throws SolrServerException, IOException{
		HttpSolrServer server = new HttpSolrServer(SolrServerAddress);
		SolrInputDocument doc = new SolrInputDocument();
		
		doc.addField("url", contentId);
        doc.addField("id", id);
        doc.addField("subject", subject);
        doc.addField("last_modified", "");
        doc.addField("cat", "to");
        doc.addField("author", author);
        doc.addField("content_type", "application/vnd.ms-outlook");
        doc.addField("content", body.replaceAll("(\\r)", ""));
        
        for(String attachment : attachments) {
           doc.addField("links", attachment);
        }
        
        server.add(doc);
        server.commit();
	}
	
	public static void indexEmail(String id,MimeMessage email) throws MessagingException, IOException {
				
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
		System.out.println(email.getFileName());
		
		Object content = email.getContent();
		
		 if (content instanceof Multipart) {
	         Multipart multipart = (Multipart) content;
	         for (int j = 0; j < multipart.getCount(); j++) {
	          BodyPart bodyPart = multipart.getBodyPart(j);
	          String disposition = bodyPart.getDisposition();
	          if (disposition != null && (disposition.equalsIgnoreCase("ATTACHMENT"))) { 
	              System.out.println("Mail have some attachment");
	              DataHandler handler = bodyPart.getDataHandler();
	              System.out.println("file name : " + handler.getName());                                 
	            }
	          else { 
	                  //content = getText(bodyPart);  // the changed code
	        	  System.out.println(bodyPart.getContent());
	            }
	        }
	     }
	     else                
	         System.out.println("Content :"+email.getContent().toString());
			
		 
		 MimeMessage message = email;
		 String result = "";
		 if(message instanceof MimeMessage)
	        {
	            MimeMessage m = (MimeMessage)message;
	            Object contentObject = m.getContent();
	            if(contentObject instanceof Multipart)
	            {
	                BodyPart clearTextPart = null;
	                BodyPart htmlTextPart = null;
	                Multipart contentPart = (Multipart)contentObject;
	                int count = contentPart.getCount();
	                for(int i=0; i<count; i++)
	                {
	                    BodyPart part =  contentPart.getBodyPart(i);
	                    if(part.isMimeType("text/plain"))
	                    {
	                        clearTextPart = part;
	                        break;
	                    }
	                    else if(part.isMimeType("text/html"))
	                    {
	                        htmlTextPart = part;
	                    }
	                }

	                if(clearTextPart!=null)
	                {
	                    result = (String) clearTextPart.getContent();
	                }
	                else if (htmlTextPart!=null)
	                {
	                    String html = (String) htmlTextPart.getContent();
	                    result = Jsoup.parse(html).text();
	                }

	            }
	             else if (contentObject instanceof String) // a simple text message
	            {
	                result = (String) contentObject;
	            }
	            else // not a mime message
	            {
	            	System.out.println("Not a Valid Message");
	            }
		
		
	            
	           
        /*doc.addField("url", email.getContentID());
        doc.addField("id", email.get);
        doc.addField("subject", msg.getSubject());
        doc.addField("last_modified", sdf.format(msg.getDate()));
        doc.addField("cat", msg.getToName());
        doc.addField("author", msg.getFromName());
        doc.addField("content_type", "application/vnd.ms-outlook");
        doc.addField("content", msg.getBodyText().replaceAll("(\\r)", ""));
        
        List<Attachment> atts = msg.getAttachments();
        for (Attachment att : atts) {
           if (att instanceof FileAttachment) {
        	   FileAttachment file = (FileAttachment) att;
        	   System.out.println("Attachment : " + file.getLongFilename());
        	   doc.addField("links", file.getLongFilename());
            }
        }
        System.out.print("******Commiting outlook mail to solr: fileName ****** \n");
        System.out.println(msg.getBodyText());
        server.add(doc);
        server.commit();*/		
		
		
		/*
		 *
		
		 Object msgContent = messages[i].getContent();

    String content = "";             
                 
     if (msgContent instanceof Multipart) {

         Multipart multipart = (Multipart) msgContent;

         Log.e("BodyPart", "MultiPartCount: "+multipart.getCount());

         for (int j = 0; j < multipart.getCount(); j++) {

          BodyPart bodyPart = multipart.getBodyPart(j);

          String disposition = bodyPart.getDisposition();

          if (disposition != null && (disposition.equalsIgnoreCase("ATTACHMENT"))) { 
              System.out.println("Mail have some attachment");

              DataHandler handler = bodyPart.getDataHandler();
              System.out.println("file name : " + handler.getName());                                 
            }
          else { 
                  content = getText(bodyPart);  // the changed code         
            }
        }
     }
     else                
         content= messages[i].getContent().toString();
		
		
		 */
	}
		 
		 System.out.println("Resul :"+result);

	}
}
