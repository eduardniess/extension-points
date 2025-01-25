/*
 * (C) Copyright 2016 Redwood Technology B.V., Houten, The Netherlands
 *
 * $Id$
 */
package custom.extensionpoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.redwood.scheduler.api.exception.ModifiedJobDefinitionCannotBePreparedException;
import com.redwood.scheduler.api.exception.ParameterDefaultValueUnavailableException;
import com.redwood.scheduler.api.exception.SchedulerAPIPersistenceException;
import com.redwood.scheduler.api.http.Part;
import com.redwood.scheduler.api.model.Document;
import com.redwood.scheduler.api.model.Job;
import com.redwood.scheduler.api.model.JobDefinition;
import com.redwood.scheduler.api.model.JobFile;
import com.redwood.scheduler.api.model.SAPSystem;
import com.redwood.scheduler.api.model.SchedulerEntity;
import com.redwood.scheduler.api.model.SchedulerSession;
import com.redwood.scheduler.api.model.Table;
import com.redwood.scheduler.api.model.enumeration.JobFileType;
import com.redwood.scheduler.api.model.interfaces.RWIterable;
import com.redwood.scheduler.api.scripting.variables.ExtensionPointHttpServletRequest;
import com.redwood.scheduler.api.scripting.variables.ExtensionPointHttpServletResponse;
import com.redwood.scheduler.api.scripting.variables.LibraryLoggerFactory;
import com.redwood.scheduler.custom.extensionpoint.library.builders.JSONBuilder;
import com.redwood.scheduler.infrastructure.logging.api.Logger;
import com.redwood.shared.infrastructure.text.html.JavaScriptString;

import custom.extensionpoint.controller.Controller;
import custom.extensionpoint.controller.CustomExtensionPointController;
import custom.extensionpoint.util.DocumentHelper;
import custom.extensionpoint.util.JobFileHelper;

public class ExtensionPointController
  extends Controller
{
  private static final long serialVersionUID = 1L;

  private static final Logger log = LibraryLoggerFactory.getLogger(CustomExtensionPointController.class);

  private static final String ORG_STRUCTURE_TABLE = "FCA_SAP_OrgStructure";

  private static final String PARTITION = "REDWOOD";

  public ExtensionPointController()
  {
    //
  }

  @Override
  public void handleRestEndpoint(ExtensionPointHttpServletRequest jcsRequest, String[] pathComponents, ExtensionPointHttpServletResponse jcsResponse,
                                 SchedulerSession jcsSession)
    throws IOException
  {
    try
    {
      log.debug("Path components: " + pathComponents);

      switch (pathComponents[2])
      {
      case "templates":
        sendResponse(jcsResponse, getTemplates(jcsSession));
        return;
      case "downloadTemplate":
        sendResponse(jcsResponse, downloadTemplate(jcsRequest, jcsSession));
        return;
      case "sapSystems":
        sendResponse(jcsResponse, getSapSystems(jcsSession));
        return;
      case "companyCodes":
        sendResponse(jcsResponse, getCompanyCodes(jcsRequest, jcsSession));
        return;
      case "submit":
        sendResponse(jcsResponse, submit(jcsRequest, jcsSession));
        return;
      }
    }
    catch (Exception e)
    {
      StringWriter sw = new StringWriter();
      try (PrintWriter pw = new PrintWriter(sw))
      {
        e.printStackTrace(pw);
        pw.flush();
      }
      log.error(e.getMessage(), e);
      sendError(jcsResponse, 404, "REST API endpoint exception: " + e.getMessage() + "  /n" + sw.toString());
      return;
    }
    sendError(jcsResponse, 404, "REST API endpoint not found: " + jcsRequest.getRequestURI() + ":" + pathComponents[2]);
  }

  private void sendResponse(ExtensionPointHttpServletResponse jcsResponse, List<String> result)
    throws IOException
  {
    @SuppressWarnings("resource")
    JSONBuilder builder = new JSONBuilder(jcsResponse.getWriter());
    JavaScriptString res = builder.startJSON();
    res.append(result);
    builder.finishJSON();

    sendResponse(jcsResponse);
  }

  private void sendResponse(ExtensionPointHttpServletResponse jcsResponse, Long result)
    throws IOException
  {
    @SuppressWarnings("resource")
    JSONBuilder builder = new JSONBuilder(jcsResponse.getWriter());
    JavaScriptString res = builder.startJSON();
    res.append(result == null ? "" : result.toString());
    builder.finishJSON();

    sendResponse(jcsResponse);
  }

  private void sendResponse(ExtensionPointHttpServletResponse jcsResponse, String result)
    throws IOException
  {
    @SuppressWarnings("resource")
    JSONBuilder builder = new JSONBuilder(jcsResponse.getWriter());
    JavaScriptString res = builder.startJSON();
    res.append(result == null ? "" : result);
    builder.finishJSON();

    sendResponse(jcsResponse);
  }

  private List<String> getTemplates(SchedulerSession jcsSession)
  {
    RWIterable<Document> it = jcsSession.executeObjectQuery("SELECT d.* FROM Document d, Application a where d.ParentApplication = a.UniqueId and a.ParentApplication is null and a.Name = 'Siemens_Templates'",
                                                            null);
    List<String> result = new ArrayList<>();
    it.forEach(d -> result.add(d.getName()));

    return result;
  }

  private List<String> getSapSystems(SchedulerSession jcsSession)
  {
    RWIterable<SAPSystem> it = jcsSession.executeObjectQuery("SELECT * FROM SAPSystem", null);
    List<String> result = new ArrayList<>();
    it.forEach(s -> result.add(s.getBusinessKey().getPath().toString()));

    return result;
  }

  private List<String> getCompanyCodes(ExtensionPointHttpServletRequest jcsRequest, SchedulerSession jcsSession)
  {
    String sapSystem = jcsRequest.getParameters().getParameter("sapSystem");
    if (sapSystem != null)
    {
      Table t = jcsSession.getTableByName(jcsSession.getPartitionByName(PARTITION), ORG_STRUCTURE_TABLE);
      if (t != null)
      {
        return Arrays.asList(sapSystem);
      }
    }
    return Collections.emptyList();
  }

  private String downloadTemplate(ExtensionPointHttpServletRequest jcsRequest, SchedulerSession jcsSession)
  {
    String template = jcsRequest.getParameters().getParameter("template");
    if (template != null)
    {
      RWIterable<Document> it = jcsSession.executeObjectQuery("SELECT d.* FROM Document d, Application a where d.Name = ? and d.ParentApplication = a.UniqueId and a.ParentApplication is null and a.Name = 'Siemens_Templates'",
                                                              new String[] { template });

      Document d = it.next();

      return DocumentHelper.getDocumentUrl(d);
    }
    return null;
  }

  private Long submit(ExtensionPointHttpServletRequest jcsRequest, SchedulerSession jcsSession)
    throws ParameterDefaultValueUnavailableException, ModifiedJobDefinitionCannotBePreparedException, SchedulerAPIPersistenceException, IllegalStateException,
    IOException
  {
    Collection<Part> parts = jcsRequest.getParts();
    JobDefinition jd = jcsSession.getJobDefinitionByName("System_Sleep");
    if (jd != null)
    {
      Job job = jd.prepare();
      if (!parts.isEmpty())
      {
        job.setHoldOnSubmit(true);

        Long jobUniqueId = job.getUniqueId();
        jcsSession.persist();

        try
        {
          jcsSession.refreshObjects((SchedulerEntity[]) null);
          job = jcsSession.getJobByUniqueId(jobUniqueId);

          Long fileOrder = JobFile.CUSTOMER_ORDER_START;
          for (Part part : parts)
          {
            log.debug("Attachning job file: " + part.getName());

            JobFile jf = job.createJobFile();
            jf.setName(part.getFileName());
            jf.setFileType(JobFileType.Output);
            jf.setFormat(JobFileHelper.getFormat(jcsSession, part.getContentType(), part.getName()));
            jf.setOrder(fileOrder);
            jf.setFileNameAutomatic();

            byte[] buffer = new byte[8192]; // 8 KB buffer size
            int bytesRead;

            File file = new File(jf.getFileName());
            File parent = file.getParentFile();
            Files.createDirectories(parent.toPath());

            try (InputStream is = part.getInputStream();
                 OutputStream os = new FileOutputStream(file, true))
            {
              while ((bytesRead = is.read(buffer)) != -1)
              {
                os.write(buffer, 0, bytesRead);
              }

              os.flush();
            }

            fileOrder = Long.valueOf(fileOrder.longValue() + 1);
          }
        }
        finally
        {
          jcsSession.refreshObjects((SchedulerEntity[]) null);
          job = jcsSession.getJobByUniqueId(jobUniqueId);

          job.release();
        }
      }

      jcsSession.persist();

      return job.getJobId();
    }
    return null;
  }
}
