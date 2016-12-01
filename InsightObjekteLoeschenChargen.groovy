import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.net.URLEncoder
import java.net.URL
import Constants;



def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()

String input = """
{ "page": 1, "asc": 1, "objectTypeId": 84, "filters": [ { "objectTypeAttributeId": 135, "selectedValues": [ "%" ] } ], "resultsPerPage": 100, "includeAttributes": true }
"""

HttpURLConnection connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
connREST.setDoOutput(true)
connREST.setRequestProperty("Content-Type", "application/json")
connREST.setRequestProperty("Authorization", "Basic ${authString}")

wr = new DataOutputStream(connREST.getOutputStream())
wr.writeBytes(input)
wr.flush()
wr.close()

responseCode = connREST.getResponseCode ()

def REST = new groovy.json.JsonSlurper().parse(connREST.getInputStream())

REST.objectEntries.each {objekt ->
    RestURL = Constants.BASE_URL_SMART + "/rest/insight/1.0/object/"+objekt.id.toString()
    connREST = new URL(RestURL).openConnection()
    connREST.setRequestMethod ("DELETE")
    connREST.setRequestProperty("Content-Type", "application/json");
    connREST.setRequestProperty("Authorization", "Basic ${authString}")
    responseCode = connREST.getResponseCode()
    log.debug objekt.id + " - " + responseCode
}
