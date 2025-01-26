/*
 * (C) Copyright 2016 Redwood Technology B.V., Houten, The Netherlands
 *
 * $Id$
 */
package custom.extensionpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.redwood.scheduler.api.http.Part;
import com.redwood.scheduler.api.model.Document;
import com.redwood.scheduler.api.model.Job;
import com.redwood.scheduler.api.model.JobDefinition;
import com.redwood.scheduler.api.model.JobFile;
import com.redwood.scheduler.api.model.SAPSystem;
import com.redwood.scheduler.api.model.SchedulerEntity;
import com.redwood.scheduler.api.model.SchedulerSession;
import com.redwood.scheduler.api.model.Table;
import com.redwood.scheduler.api.model.interfaces.RWIterable;
import com.redwood.scheduler.api.scripting.variables.ExtensionPointHttpServletRequest;
import com.redwood.scheduler.api.scripting.variables.ExtensionPointHttpServletResponse;
import com.redwood.scheduler.api.scripting.variables.LibraryLoggerFactory;
import com.redwood.scheduler.infrastructure.logging.api.Logger;

import custom.extensionpoint.controller.Controller;
import custom.extensionpoint.util.DocumentHelper;

public class ExtensionPointController
  extends Controller
{
  private static final long serialVersionUID = 1L;

  private static final Logger log = LibraryLoggerFactory.getLogger(ExtensionPointController.class);

  private static final String ORG_STRUCTURE_TABLE = "FCA_SAP_OrgStructure";
  private static final String TEMPLATE_APPLICATION = "Siemens_Templates";

  private static final String PARTITION = "REDWOOD";

  public ExtensionPointController()
  {
    //
  }

  @Override
  public boolean handleRestEndpoint(ExtensionPointHttpServletRequest jcsRequest, String[] pathComponents, ExtensionPointHttpServletResponse jcsResponse,
                                    SchedulerSession jcsSession)
    throws Exception
  {
    log.debug("Path components: " + pathComponents);

    switch (pathComponents[2])
    {
    case "templates":
      sendResponse(jcsResponse, getTemplates(jcsSession));
      return true;
    case "downloadTemplate":
      sendResponse(jcsResponse, downloadTemplate(jcsRequest, jcsSession));
      return true;
    case "sapSystems":
      sendResponse(jcsResponse, getSapSystems(jcsSession));
      return true;
    case "companyCodes":
      sendResponse(jcsResponse, getCompanyCodes(jcsRequest, jcsSession));
      return true;
    case "submit":
      sendResponse(jcsResponse, submit(jcsRequest, jcsSession));
      return true;
    }

    return false;
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
      RWIterable<Document> it = jcsSession.executeObjectQuery("SELECT d.* FROM Document d, Application a where d.Name = ? and d.ParentApplication = a.UniqueId and a.ParentApplication is null and a.Name = ?",
                                                              new String[] { template, TEMPLATE_APPLICATION });

      Document d = it.next();

      return DocumentHelper.getDocumentUrl(d);
    }
    return null;
  }

  private Long submit(ExtensionPointHttpServletRequest jcsRequest, SchedulerSession jcsSession)
    throws Exception
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

            createJobFile(jcsSession, job, fileOrder, part);

            fileOrder = Long.valueOf(fileOrder.longValue() + 1);
          }

          job.release();
        }
        catch (IOException e)
        {
          jcsSession.reset();
          job = jcsSession.getJobByUniqueId(jobUniqueId);

          job.cancel();
        }
      }

      jcsSession.persist();

      return job.getJobId();
    }
    return null;
  }
}
