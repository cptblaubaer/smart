import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.net.URLEncoder
import java.net.URL
import groovy.sql.Sql
import java.sql.Driver
import Constants

if (Constants.SYSTEM != 'productive')
{
  println "Testsystem"
}
else
{
   println "Produktivsystem"
}

connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objecttype/4/objects?query=" + sapmaterialnummer).openConnection()
connREST.setDoOutput(false)
connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
connREST.setRequestProperty("Authorization", "Basic ${authString}")
InsightObjekt = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
println InsightObjekt.toPrettyString()
