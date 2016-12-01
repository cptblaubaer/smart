import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.net.URLEncoder
import java.lang.invoke.MethodHandleNatives.Constants;
import java.net.URL
import groovy.sql.Sql
import java.sql.Driver
import Constants

/*if (Constants.SYSTEM != 'productive') {
	log.debug "Groovy Script kann nur auf dem Produktiv-System ausgeführt werden."
	return;
}
*/

//Datenbankverbindung Oracle
def driver = Class.forName('oracle.jdbc.OracleDriver').newInstance() as Driver
def props = new Properties()
props.setProperty("user", "sm")
props.setProperty("password", "sonsdihdf")
String dbServer
if (Constants.SYSTEM != 'productive')
{
  dbServer = "flstest"
}
else
{
  dbServer = "oraprod"
}
def conn = driver.connect("""jdbc:oracle:thin:@$dbServer:1521:FS4P""", props)
def sql = new Sql(conn)
def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()


//Abfrage aller Materialien, die aktualisiert werden müssen
String materialienSql = """
	SELECT * FROM sm.IH_STUECKLISTE
	WHERE FLAG_AKTUALISIERUNG = 1
"""

def materialienToUpdate = sql.rows (materialienSql)
if (materialienToUpdate.isEmpty()) 
{
	log.debug "Keine Materialien gefunden, die aktualisiert werden müssen.";
	return;
}

def querySingleInsightObjectAsRest(queryRestString, authString) {
	HttpURLConnection conn = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
	conn.setDoOutput(true)
	conn.setRequestProperty("Content-Type", "application/json");
	conn.setRequestProperty("Authorization", "Basic ${authString}")
	
	def result = new DataOutputStream(conn.getOutputStream())
	result.writeBytes(queryRestString)
	result.flush()
	result.close()
	
	def response = new groovy.json.JsonSlurper().parse(conn.getInputStream())
	if (response.totalFilterCount == 1) 
	{
		return response.objectEntries[0];
	} 
	else 
	{
		return null;
	}
}

def findAnlageInsightObject(sapObjektnummer, authString) 
{
	String querySAPObject = """
		{ "page": 1, "asc": 1, "objectTypeId": 41, "filters": [ { "objectTypeAttributeId": 85, "selectedValues": [ "@@SAPObjektnummer@@" ] } ], "resultsPerPage": 999999, "includeAttributes": true }
	"""
	querySAPObject = querySAPObject.replace("@@SAPObjektnummer@@", sapObjektnummer.toString())
	return querySingleInsightObjectAsRest(querySAPObject, authString);
}

String statementAktualisierungZuruecksetzen = """
	UPDATE sm.IH_STUECKLISTE SET flag_aktualisierung = 0 WHERE materialnr = ? AND objektapk = ?
"""

// Map Materialien/Insight Object erzeugen
String QueryMaterialien = """
	{ "page": 1, "asc": 1, "objectTypeId": 4, "filters": [ { "objectTypeAttributeId": 44, "selectedValues": [ "%" ] } ], "resultsPerPage": 999999, "includeAttributes": true }
"""

HttpURLConnection connMaterialien = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
connMaterialien.setDoOutput(true)
connMaterialien.setRequestProperty("Content-Type", "application/json");
connMaterialien.setRequestProperty("Authorization", "Basic ${authString}")
def wrMaterialien = new DataOutputStream(connMaterialien.getOutputStream())
wrMaterialien.writeBytes(QueryMaterialien)
wrMaterialien.flush()
wrMaterialien.close()

def Materialien = new groovy.json.JsonSlurper().parse(connMaterialien.getInputStream())

def MapMaterialien = [:]
String WgrpNrSAP = ""
String WgrpIdInsight = ""

Materialien.objectEntries.each 
{mat ->
	MatIdInsight = mat.id
	mat.attributes.each 
	{attr->
		if (attr.objectTypeAttributeId == 44) 
		{
			MatNrSAP=attr.objectAttributeValues.getAt(0).value
		}
	}
	MapMaterialien.put (MatNrSAP, mat)
}

//log.debug MapMaterialien


for (materialToUpdate in materialienToUpdate) // Schleife über die Datenbankeinträge aus sm.IH_STUECKLISTE
{
	def materialInsightObject = MapMaterialien.get(materialToUpdate.materialnr);
	def materialInsightObjectId = materialInsightObject.id
	if (materialInsightObjectId == null) {
		log.debug "Material mit SAP-Materialnummer '" + materialToUpdate.materialnr + "' kann nicht gefunden werden.";
		continue;
	}
	
	def anlageInsightObject = findAnlageInsightObject(materialToUpdate.objektapk, authString);
	if (anlageInsightObject == null) {
		log.debug "Objekt mit SAP-Objektnummer '" + materialToUpdate.objektapk + "' kann nicht gefunden werden.";
		continue;
	}
	
	// Holen wir uns einmal die Informationen zu den aktuell am Objekt eingetragenen SAP Stücklisten

	
	if (materialInsightObject==null)
	{
		log.debug "Zugriff über ID hat nicht funktioniert " + materialInsightObjectId
	}
	else
	{
		log.debug "Verwendung Stückliste für " + materialInsightObject.id + " sieht wie folgt aus:"
		materialInsightObject.attributes.each
		{attr->
			if (attr.objectTypeAttributeId == 801) 
			{
				anzahl = attr.objectAttributeValues.size()
				log.debug "Es wurden " + anzahl + " Verwendungen gefunden."
				attr.objectAttributeValues.each
				{verwendung ->
					log.debug verwendung.referencedObject.id + " ist " + verwendung.referencedObject.name
				}
				/*
				verwendungStuecklisteValue = attr.objectAttributeValues
				builderVerwendungStueckliste = new groovy.json.JsonBuilder(verwendungStuecklisteValue)
				log.debug "Struktur " + builderVerwendungStueckliste.toPrettyString()
				*/
			}
		}
	}
	
	def newVerwendungStuecklisteValue
	if (materialToUpdate.flag_geloescht == 1) 
	{
		newVerwendungStuecklisteValue = null;
	} 
	else 
	{
		newVerwendungStuecklisteValue = anlageInsightObject.id;
	}
	/*
	def ListAttribute = []
	MapAttribut = new HashMap()
	MapAttribut = ["objectTypeAttributeId": 801]
	MapWert = new HashMap()
	MapWert = ["value": newVerwendungStuecklisteValue]
	MapAttribut.put ("objectAttributeValues", [MapWert])
	ListAttribute.add(MapAttribut)
	MapObjekt = new HashMap()
	MapObjekt.put ("attributes", ListAttribute)
	builderAttribut = new groovy.json.JsonBuilder (MapObjekt)

	connAttribut = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/" + materialInsightObjectId.toString()).openConnection()
	connAttribut.setDoOutput(true)
	connAttribut.setRequestProperty("Content-Type", "application/json; charset=utf-8")
	connAttribut.setRequestProperty("Authorization", "Basic ${authString}")
	connAttribut.setRequestMethod("PUT")
	
	wrAttribut = new DataOutputStream(connAttribut.getOutputStream())
	wrAttribut.writeBytes(builderAttribut.toPrettyString())
	wrAttribut.flush()
	wrAttribut.close()
  // log.debug builderAttribut.toPrettyString()
	try {
		AntwortAttribut = new groovy.json.JsonSlurper().parse(connAttribut.getInputStream())
	
		if (materialToUpdate.flag_geloescht == 1) {
			log.debug "Materialverknüpfung entfernt. Material-Nr: " + materialToUpdate.materialnr.toString() + ", Material-Objekt: " + materialInsightObjectId + "."
		} else {
			log.debug "Materialverknüpfung aktualisiert. Material-Nr: " + materialToUpdate.materialnr.toString() + ", Material-Objekt: " + materialInsightObjectId + ", Anlage-Objekt: " + anlageInsightObject.id + "(" + materialToUpdate.objektapk.toString() + ")."
		}
	
		sql.execute (statementAktualisierungZuruecksetzen, [materialToUpdate.materialnr, materialToUpdate.objektapk])
	} catch (Exception exception) {
		log.debug "Fehler beim Aktualisieren des Materials " + materialToUpdate.materialnr.toString() + ". Exception: " + exception
	}
	*/
}
