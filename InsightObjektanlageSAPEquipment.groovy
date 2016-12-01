import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.net.URLEncoder
import java.net.URL
import groovy.sql.Sql
import java.sql.Driver
import Constants

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

def AbfrageEquipment = """
select * from (
select
  io.apk,
  io.bezeichnung,
  parent.JIRA_ID id_parent,
  tp.JIRA_ID id_techplatz,
  tp.apk apk_techplatz
from sm.ih_objekt io
join sm.ih_objekt parent on parent.apk=io.UEBERG_OBJEKT
join sm.ih_objekt tp on tp.APK=io.TECHN_PLATZ
where parent.JIRA_ID is not null and
  io.UEBERG_OBJEKT is not null and
  io.jira_key is null
) where rownum<51
"""

def AbfrageStueckliste = """
  select
    ms.JIRA_id
  from sm.IH_STUECKLISTE sl
  join sm.SAP_MATERIALSTAMM ms on ms.materialnr=sl.materialnr
  where objektapk=?
"""

String statementSchluesselAblegen = """
    update sm.ih_objekt set jira_id=?, jira_key=? where apk=?
  """


def rsEquipment = sql.rows (AbfrageEquipment)

def AttributeRoh = []
def objectTypeId=41
def MapObjekt = new HashMap()
def MapAttribut = new HashMap()
def MapWert = new HashMap()


if (rsEquipment.isEmpty()==false) {
    rsEquipment.each {eq ->
        log.debug eq.apk.toString()

        rsStueckliste = sql.rows (AbfrageStueckliste,[eq.apk])

        EintraegeStueckliste = []
        rsStueckliste.each {sl ->
            EintraegeStueckliste.add (sl.jira_id)
        }

        AttributeRoh = []
        AttributeRoh.add ([id:81,ListEinzelwerte:[eq.bezeichnung.toString()]])
        AttributeRoh.add ([id:85,ListEinzelwerte:[eq.apk.toString()]])
        AttributeRoh.add ([id:86,ListEinzelwerte:[eq.id_parent]])
        MapObjekt = new HashMap()
        MapAttribut = new HashMap()
        MapWert = new HashMap()
        ListAttribute = []

        MapObjekt.put ("objectTypeId",objectTypeId)
        AttributeRoh.each {attribut ->
            MapAttribut = ["objectTypeAttributeId": attribut.id]
            ListWerte = []
            attribut.ListEinzelwerte.each {einzelwert ->
                MapWert = ["value": einzelwert]
                ListWerte.add (MapWert)
            }
            MapAttribut.put ("objectAttributeValues",ListWerte)
            ListAttribute.add(MapAttribut)
        }
        MapObjekt.put ("attributes",ListAttribute)

        builder = new groovy.json.JsonBuilder (MapObjekt)
	
	log.debug builder.toPrettyString()

        connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
        connREST.setDoOutput(true)
        connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connREST.setRequestProperty("Authorization", "Basic ${authString}")

        wr = new DataOutputStream(connREST.getOutputStream())
        wr.writeBytes(builder.toPrettyString())
        wr.flush()
        wr.close()

        EquipmentInsight = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
        sql.execute (statementSchluesselAblegen,[EquipmentInsight.id, EquipmentInsight.objectKey, eq.apk])
    }
}
else {
    log.debug "Keine neu anzulegenden Equipments gefunden."
}