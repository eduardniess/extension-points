/*
 * (C) Copyright 2016 Redwood Technology B.V., Houten, The Netherlands
 *
 * $Id$
 */
package com.redwood.custom.extensionpoint;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.redwood.custom.extensionpoint.controller.Controller;
import com.redwood.custom.extensionpoint.controller.CustomExtensionPointController;
import com.redwood.scheduler.api.exception.ModifiedJobDefinitionCannotBePreparedException;
import com.redwood.scheduler.api.exception.ParameterDefaultValueUnavailableException;
import com.redwood.scheduler.api.exception.SchedulerAPIPersistenceException;
import com.redwood.scheduler.api.model.Job;
import com.redwood.scheduler.api.model.JobDefinition;
import com.redwood.scheduler.api.model.SAPSystem;
import com.redwood.scheduler.api.model.SchedulerSession;
import com.redwood.scheduler.api.model.Table;
import com.redwood.scheduler.api.model.interfaces.RWIterable;
import com.redwood.scheduler.api.scripting.variables.ExtensionPointHttpServletRequest;
import com.redwood.scheduler.api.scripting.variables.ExtensionPointHttpServletResponse;
import com.redwood.scheduler.api.scripting.variables.LibraryLoggerFactory;
import com.redwood.scheduler.custom.extensionpoint.library.builders.JSONBuilder;
import com.redwood.scheduler.infrastructure.logging.api.Logger;
import com.redwood.shared.infrastructure.text.html.JavaScriptString;

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
      case "sapSystems":
        sendResponse(jcsResponse, getSapSystems(jcsSession));
        return;
      case "companyCodes":
        sendResponse(jcsResponse, getCompanyCodes(jcsRequest, jcsSession));
        return;
      case "submit":
        sendResponse(jcsResponse, submit(jcsRequest, jcsSession));
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

  private Long submit(ExtensionPointHttpServletRequest jcsRequest, SchedulerSession jcsSession)
    throws ParameterDefaultValueUnavailableException, ModifiedJobDefinitionCannotBePreparedException, SchedulerAPIPersistenceException
  {
    JobDefinition jd = jcsSession.getJobDefinitionByName("System_Sleep");
    if (jd != null)
    {
      Job job = jd.prepare();
      jcsSession.persist();

      return job.getJobId();
    }
    return null;
  }
}
