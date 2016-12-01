import groovy.sql.Sql
import java.sql.Driver
import java.text.SimpleDateFormat
import Constants;

if (Constants.SYSTEM != 'productive') {
        log.debug "Groovy Script kann nur auf dem Produktiv-System ausgeführt werden."
        return;
}

def driver = Class.forName('oracle.jdbc.OracleDriver').newInstance() as Driver

def props = new Properties()
props.setProperty("user", "sm")
props.setProperty("password", "sonsdihdf")

def conn = driver.connect("jdbc:oracle:thin:@oraprod:1521:FS4P", props)
def sql = new Sql(conn)

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()

def abfrageArbeitsvorrat = """
  select
    io.JIRA_ID,
    io.apk,
    io.BEZEICHNUNG,
    io.TECHN_PLATZ,
    parent.JIRA_ID parent
  from sm.IH_OBJEKT io
  left outer join sm.IH_OBJEKT parent on parent.apk=io.UEBERG_OBJEKT
  where ((io.FLAG_AKTUALISIERUNG is null) or (io.FLAG_AKTUALISIERUNG=1)) and
    io.typ='EQUIPMENT' and
    rownum<101
"""

def statementFlagSetzen = """
  update sm.ih_objekt set flag_aktualisierung=0 where jira_id=?
"""

def int JiraidAktuell = 0
def attributName = ""
def attributParent = ""
HashMap MapObjekt

def rsArbeitsvorrat = sql.rows(abfrageArbeitsvorrat)
if (rsArbeitsvorrat.isEmpty()==false) {
    //log.debug rsArbeitsvorrat.toString()
    rsArbeitsvorrat.each {objekt ->
        JiraidAktuell = objekt.jira_id.intValue()
        connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/"+JiraidAktuell.toString()).openConnection()
        connREST.setDoOutput(false)
        connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connREST.setRequestProperty("Authorization", "Basic ${authString}")
        EquipmentInsight = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
        EquipmentInsight.attributes.each {attribut ->
            //Name
            if (attribut.objectTypeAttributeId==81) {
                attributName=attribut.objectAttributeValues[0].value
                attributIdName=attribut.id
            }
            //Name
            if (attribut.objectTypeAttributeId==86) {
                attributParent=attribut.objectAttributeValues[0].referencedObject.id
                attributIdParent=attribut.id
            }
        }
        if ((attributName==objekt.bezeichnung) && (attributParent==objekt.parent)) {
            sql.execute (statementFlagSetzen,[JiraidAktuell])
            //log.debug "Equipment " + objekt.apk + " - Informationen sind aktuell"
        }
        else {
            if (attributName!=objekt.bezeichnung) {
                MapObjekt = new HashMap()
                MapObjekt.put ("objectAttributeValues",[["value":objekt.bezeichnung]])
                attributIdAktuell = attributIdName
            }
            else {
                if (attributParent!=objekt.parent) {
                    MapObjekt = new HashMap()
                    MapObjekt.put ("objectAttributeValues",[["value":objekt.parent.intValue() ]])
                    attributIdAktuell = attributIdParent
                }
            }
            builder = new groovy.json.JsonBuilder (MapObjekt)
            connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectattribute/"+attributIdAktuell.toString()).openConnection()
            connREST.setDoOutput(true)
            connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connREST.setRequestProperty("Authorization", "Basic ${authString}")
            connREST.setRequestMethod("PUT")
            wr = new DataOutputStream(connREST.getOutputStream())
            log.debug builder.toPrettyString()
            wr.writeBytes(builder.toPrettyString())
            wr.flush()
            wr.close()
            Antwort = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
            log.debug  "Equipment " + objekt.apk + " - Unterschied gefunden"
        }
    }
    return rsArbeitsvorrat.size().toString() + " Objekt(e) bearbeitet."
}
else {
    return "Keine zu bearbeitenden Objekte gefunden."
}

