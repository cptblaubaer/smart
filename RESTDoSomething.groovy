import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import groovy.json.JsonBuilder
import groovy.transform.BaseScript
import java.text.SimpleDateFormat

import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response

@BaseScript CustomEndpointDelegate delegate

SimpleDateFormat dfZeitstempel = new SimpleDateFormat("HH:mm:ss")

doSomething(httpMethod: "GET", groups: ["jira-administrators"]) { MultivaluedMap queryParams, String body ->
  def datum = new Date()
  String antwort=""
  antwort = antwort + "{panel}"
  antwort = antwort + dfZeitstempel.format(datum)
  antwort = antwort + "{panel}"
    return Response.ok(antwort).build()
 }   