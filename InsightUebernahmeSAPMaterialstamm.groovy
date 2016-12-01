import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.io.*
import java.net.URLEncoder
import java.net.URL
import groovy.sql.Sql
import java.sql.Driver
import Constants

/* if (Constants.SYSTEM != 'productive') {
        log.debug "Groovy Script kann nur auf dem Produktiv-System ausgeführt werden."
        return;
} */

def getSapWarengruppeId(String Warengruppe, HashMap sapWarengruppen)
{
	if (sapWarengruppen.containsKey(Warengruppe))
	{
		return sapWarengruppen[Warengruppe]
	}
	else
	{
		sapWarengruppen.put(Warengruppe, sapWarengruppen.size()+1)
		return sapWarengruppen[Warengruppe]
	}
}


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

log.debug """Verbunden mit $dbServer."""

// Abfrage existierende Warengruppen in Insight, die ObjektIds lassen sich dann anschließend anhand der Bezeichnung suchen
HttpURLConnection connSAPWgrp = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
connSAPWgrp.setDoOutput(true)
connSAPWgrp.setRequestProperty("Content-Type", "application/json");
connSAPWgrp.setRequestProperty("Authorization", "Basic ${authString}")
wrSAPWgrp = new DataOutputStream(connSAPWgrp.getOutputStream())
wrSAPWgrp.writeBytes(QuerySAPWarengruppe)
wrSAPWgrp.flush()
wrSAPWgrp.close()
InsightSAPWgrp = new groovy.json.JsonSlurper().parse(connSAPWgrp.getInputStream())
sapWarengruppen = new HashMap()
InsightSAPWgrp.objectEntries.each
{wgrp ->
	wgrp.attributes.each
	{attribut ->
		// SAP-Warengruppe hat die objectTypeAttributeId 15
  	if (attribut.objectTypeAttributeId==15)
		{
			Warengruppe = attribut.objectAttributeValues[0].value
			sapWarengruppen.put(Warengruppe, attribut.id)
		}
	}
}

def AbfrageMaterialStamm = """
select *
from  sm.SAP_MATERIALSTAMM
where FLAG_AKTUALISIERUNG = 9
order by MATERIALNR"""

def statementFlagSetzen = """
  update sm.SAP_MATERIALSTAMM set FLAG_AKTUALISIERUNG=0 where MATERIALNR=?
"""

def AusfuehrungOK = true

// 

// Arbeitsvorrat abholen
def rsMaterialStamm = sql.rows (AbfrageMaterialStamm)

if (rsMaterialStamm.isEmpty()==false)
{
  // zu bearbeitende Materialstämme sind vorhanden
  rsMaterialStamm.each
  {ms ->
		// Für jedes Material erst einmal von einer erfolgreichen Ausführung ausgehen
		AusfuehrungOK 			= true
    name                = ms.MATERIALNR + " | " + ms.MATERIALKURZTEXT.trim()
    materialkurztext    = ms.MATERIALKURZTEXT
    bme                 = ms.BME
    sapmaterialnummer   = ms.MATERIALNR
    loevm               = ms.LOEVM
    wgrp                = ms.WGRP
    // log.debug "Materialstamm: " + name
    // nach passendem Materialstamm suchen, über das Attribut mit der ID 44 für die SAP Materialnummer
    String queryMaterialstaemme = """
    { "page": 1, "asc": 1, "objectTypeId": 4, "filters": [ { "objectTypeAttributeId": 44, "selectedValues": [ "$sapmaterialnummer" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
    """
    connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
    connREST.setDoOutput(true)
    connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    connREST.setRequestProperty("Authorization", "Basic ${authString}")
    def wrMaterialstaemme = new DataOutputStream(connREST.getOutputStream())
    wrMaterialstaemme.writeBytes(queryMaterialstaemme)
    wrMaterialstaemme.flush()
    wrMaterialstaemme.close()
    sapMaterialInsight = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
    // Es wurde kein passender Materialstamm gefunden
		if (sapMaterialInsight.totalFilterCount==0)
    {
			// löschvorgemerkte Materialstämme werden nicht hinzugefügt	
      if (loevm)
      {
        log.debug "Material " + name + " ist löschvorgemerkt und wird daher nicht angelegt."
        sql.execute (statementFlagSetzen,[sapmaterialnummer])
      }
      // wirklich erforderliche Neuanlage
			else
      {
        log.debug "Neuanlage von " + name
        def AttributeRoh  = []
        // Objekttyp des zu erstellenden Objekts hier die ID 4 für das SAP-Material
        def objectTypeId  = 4
        // Eigentliches Objekt
        def MapObjekt     = new HashMap()
        // Attribute zum Objekt
        def MapAttribut   = new HashMap()
        // Werte zu den Attributen
        def MapWert       = new HashMap()
        AttributeRoh      = []
        // Objekttyp festlegen
        MapObjekt.put ("objectTypeId",objectTypeId)
        // Liste für die Attribute erstellen
        AttributeRoh = []
        // Name eintragen
        AttributeRoh.add([id:  18, ListEinzelwerte: [name]])
        // Materialkurztext eintragen
        AttributeRoh.add([id:  41, ListEinzelwerte: [materialkurztext]])
        // BME eintragen
        AttributeRoh.add([id:  42, ListEinzelwerte: [bme]])
        // SAP Materialnummer eintragen
        AttributeRoh.add([id:  44, ListEinzelwerte: [sapmaterialnummer]])
        // SAP-Materialtyp leer bei Neuanlage
        AttributeRoh.add([id:  77, ListEinzelwerte: []])
        // Verwendung Stückliste leer bei Neuanlage
        AttributeRoh.add([id: 801, ListEinzelwerte: []])
				/*
				// Warengruppe suchen (Objekttyp 3)
        connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objecttype/3/objects?query=" + wgrp).openConnection()
        connREST.setDoOutput(false)
        connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connREST.setRequestProperty("Authorization", "Basic ${authString}")
        sapWarengruppe = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
        */
        if (!sapWarengruppen.containsKey(wgrp))
        {
          log.debug "Warengruppe " + wgrp + " ist noch nicht vorhanden und muss angelegt werden."
          def MapWarengruppeObjekt    = new HashMap()
          def MapWarengruppeAttribut  = new HashMap()
          def MapWarengruppeWert      = new HashMap()
          AttributeWarengruppeRoh = []
          // Name
          AttributeWarengruppeRoh.add([id:   11, ListWarengruppeEinzelwerte: [wgrp + " | N.N. benenne mich doch mal bitte um."]])
          // SAP-Warengruppe
          AttributeWarengruppeRoh.add([id:   15, ListWarengruppeEinzelwerte: [wgrp]])
          ListWarengruppeAttribute = []
          MapWarengruppeObjekt.put("objectTypeId", 3)
          AttributeWarengruppeRoh.each
          {attribut ->
            MapAttribut = ["objectTypeAttributeId": attribut.id]
            ListWerte = []
            attribut.ListWarengruppeEinzelwerte.each
            {einzelwert ->
              MapWert = ["value": einzelwert]
              ListWerte.add (MapWert)
            }
            // Werte für die Attribute übermitteln
            MapAttribut.put ("objectAttributeValues",ListWerte)
            ListWarengruppeAttribute.add(MapAttribut)
          }
					// Attribute zur neuen Warengruppe hinzufügen
          MapWarengruppeObjekt.put ("attributes",ListWarengruppeAttribute)
          // JSON String generieren
					builder = new groovy.json.JsonBuilder (MapWarengruppeObjekt)
          connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
          connREST.setDoOutput(true)
          connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
          connREST.setRequestProperty("Authorization", "Basic ${authString}")

          // Objekt schreiben
          wr = new DataOutputStream(connREST.getOutputStream())
          //log.debug builder.toPrettyString()
          wr.writeBytes(builder.toPrettyString())
          wr.flush()
          wr.close()
          // neu angelegtes Objekt abholen
          sapWarengruppe = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
          sapWarengruppeId = sapWarengruppe.id
        } // end of if (sapWarengruppe.isEmpty())
        sapWarengruppeId = getSapWarengruppenId(wgrp)
        // SAP Warengruppe eintragen, Objektreferenz jeweils durch eintragen der zugehörigen ID
        AttributeRoh.add([id:  43, ListEinzelwerte: [sapWarengruppeId.toString()]])

        ListAttribute = []
        MapObjekt.put ("objectTypeId",objectTypeId)
        AttributeRoh.each
        {attribut ->
          MapAttribut = ["objectTypeAttributeId": attribut.id]
          ListWerte = []
          attribut.ListEinzelwerte.each
          {einzelwert ->
            MapWert = ["value": einzelwert]
            ListWerte.add (MapWert)
          }
          // Werte für die Attribute übermitteln
          MapAttribut.put ("objectAttributeValues",ListWerte)
          ListAttribute.add(MapAttribut)
        }
        MapObjekt.put ("attributes",ListAttribute)
        builder = new groovy.json.JsonBuilder (MapObjekt)
        //log.debug builder.toPrettyString()
        connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
        connREST.setDoOutput(true)
        connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connREST.setRequestProperty("Authorization", "Basic ${authString}")

        // Objekt schreiben
        wr = new DataOutputStream(connREST.getOutputStream())
        wr.writeBytes(builder.toPrettyString())
        wr.flush()
        wr.close()
        // neu angelegtes Objekt abholen
        sapMaterialInsight = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
        log.debug "Materialstamm wurde mit der ID " + sapMaterialInsight.id.toString() + " angelegt."
        sql.execute (statementFlagSetzen,[sapmaterialnummer])
      }
    }
    else if (sapMaterialInsight.totalFilterCount==1) // genau ein Objekt ist vorhanden und muss auf Aktualisierung geprüft werden
    {
      // Objekte mit gesetztem Flag für die Löschvormerkung werden gelöscht
      if (loevm)
      {
				// Das zur Löschung anstehende Objekt ist das erste in der Liste der Suchergebnisse  
				delSapMaterialInsight = sapMaterialInsight.objectEntries[0]
        log.debug sapmaterialnummer + " muss gelöscht werden. Hat die Objekt-ID " + delSapMaterialInsight.id.toString()
        RestURL = Constants.BASE_URL_SMART + "/rest/insight/1.0/object/" + delSapMaterialInsight.id.toString()
        connREST = new URL(RestURL).openConnection()
        connREST.setRequestMethod ("DELETE")
        connREST.setRequestProperty("Content-Type", "application/json");
        connREST.setRequestProperty("Authorization", "Basic ${authString}")
        responseCode = connREST.getResponseCode()
        log.debug delSapMaterialInsight.id + " - " + responseCode
      }
      else
      {
		  	// Das aktuell zur Änderung anstehende Objekt ist das erste in der Liste der Suchergebnisse  
        updSapMaterialInsight = sapMaterialInsight.objectEntries[0]
        log.debug "Prüfung auf Aktualisierung von " + updSapMaterialInsight.name 
        // Attribute des aktuellen Materialstamm auswerten
        updSapMaterialInsight.attributes.each
        {attribut ->
            def attributName = null
            def attributIdName = null
            def attributMaterialkurztext = null
            def attributIdMaterialkurztext = null
            def attributWGrp = null
            def attributIdWGrp = null
            def attributBme = null
            def attributIdBme = null
            def attributMaterialnummer = null
            def attributIdMaterialnummer = null
            match = false
            // Name
            if (attribut.objectTypeAttributeId == 18)
            {
                attributName = attribut.objectAttributeValues[0].value
                attributIdName = attribut.id
                match = true
            }
            // Materialkurztext
            if (attribut.objectTypeAttributeId == 41)
            {
                attributMaterialkurztext = attribut.objectAttributeValues[0].value
                attributIdMaterialkurztext = attribut.id
                match = true
            }
            // BME
            if (attribut.objectTypeAttributeId == 42)
            {
                attributBme = attribut.objectAttributeValues[0].value
                attributIdBme = attribut.id
                match = true
            }
            // SAP Warengruppe
            if (attribut.objectTypeAttributeId == 43)
            {
							// log.debug attribut.id + " ist die ID von " + attribut
							if (attribut.id != null)
							{
								// die ersten sieben Zeichen sind die Bezeichnung der Warengruppe
								attributWGrp = attribut.objectAttributeValues[0].referencedObject.name.toString()[0..6]
								attributIdWGrp = attribut.id
								match = true
							}
							else // Der Fall darf eigentlich nicht auftreten, "leeres" Warengruppenobjekt wie z.B. [objectAttributeValues:[], objectId:200619, objectTypeAttributeId:43]
							{
								log.debug attribut
								attributWGrp = "???"
								match = false
								// Weiteres Vorgehen noch abstimmen, löschen des Materialstamms? 
								log.error "Fehlende Warengruppe bei Materialnummer " + sapmaterialnummer
								AusfuehrungOK = false
							}
            }
            // SAP Materialnummer
            if (attribut.objectTypeAttributeId == 44)
            {
                attributMaterialnummer = attribut.objectAttributeValues[0].value
                attributIdMaterialnummer = attribut.id
                match = true
            }
						// Liste von geänderten Attributen erstellen
						geaenderteAttribute = []
						// Name unterschiedlich?
						if ((match) && (attributName != name) && (attributName != null))
						{
							log.debug attributName + " -> " + name + attributName.class + " " + name.class + " " + attributName.length() + " " + name.length()
							MapObjekt = new HashMap()
							MapObjekt.put ("objectAttributeValues",[["value":name]])
							geaenderteAttribute.add([id:  attributIdName, Wert: MapObjekt])

						}
						// Materialkurztext unterschiedlich?
						if ((match) && (attributMaterialkurztext != materialkurztext) && (attributMaterialkurztext != null))
						{
							MapObjekt = new HashMap()
							MapObjekt.put ("objectAttributeValues",[["value":materialkurztext]])
							geaenderteAttribute.add([id:  attributIdMaterialkurztext, Wert: MapObjekt])
							log.debug attributMaterialkurztext + " -> " + materialkurztext
						}
						// Warengruppe unterschiedlich?
						if ((match) && (attributWGrp != wgrp) && (attributWGrp != null))
						{
							log.debug attributWGrp + " -> " + wgrp + attributWGrp.length() + " " + wgrp.length()
							// Warengruppe suchen (Objekttyp 3)
							connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objecttype/3/objects?query=" + wgrp).openConnection()
							connREST.setDoOutput(false)
							connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
							connREST.setRequestProperty("Authorization", "Basic ${authString}")
							sapUpdWarengruppe = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
							if (sapUpdWarengruppe.isEmpty())
							{
								log.debug "Warengruppe " + wgrp + " ist noch nicht vorhanden und muss angelegt werden."
								def MapWarengruppeObjekt    = new HashMap()
								def MapWarengruppeAttribut  = new HashMap()
								def MapWarengruppeWert      = new HashMap()
								AttributeWarengruppeRoh = []
								// Name
								AttributeWarengruppeRoh.add([id:   11, ListWarengruppeEinzelwerte: [wgrp + " | N.N. benenne mich doch mal bitte um."]])
								// SAP-Warengruppe
								AttributeWarengruppeRoh.add([id:   15, ListWarengruppeEinzelwerte: [wgrp]])
								ListWarengruppeAttribute = []
								MapWarengruppeObjekt.put("objectTypeId", 3)
								AttributeWarengruppeRoh.each
								{neuattribut ->
									MapAttribut = ["objectTypeAttributeId": neuattribut.id]
									ListWerte = []
									neuattribut.ListWarengruppeEinzelwerte.each
									{einzelwert ->
										MapWert = ["value": einzelwert]
										ListWerte.add (MapWert)
									}
									// Werte für die Attribute übermitteln
									MapAttribut.put ("objectAttributeValues",ListWerte)
									ListWarengruppeAttribute.add(MapAttribut)
								}				
								// Attribute zur neuen Warengruppe hinzufügen
								MapWarengruppeObjekt.put ("attributes",ListWarengruppeAttribute)
								// JSON String generieren
								builder = new groovy.json.JsonBuilder (MapWarengruppeObjekt)
								connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
								connREST.setDoOutput(true)
								connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
								connREST.setRequestProperty("Authorization", "Basic ${authString}")
								// Objekt schreiben
								wr = new DataOutputStream(connREST.getOutputStream())
								wr.writeBytes(builder.toPrettyString())
								wr.flush()
								wr.close()
								// neu angelegtes Objekt abholen
								sapUpdWarengruppe = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
								sapUpdWarengruppeId = sapUpdWarengruppe.id
								MapObjekt = new HashMap()
								MapObjekt.put ("objectAttributeValues",[["value":sapUpdWarengruppe.id.toString()]])
								geaenderteAttribute.add([id:  attributIdWGrp, Wert: MapObjekt])
							}
							else
							{
								MapObjekt = new HashMap()
								MapObjekt.put ("objectAttributeValues",[["value":sapUpdWarengruppe.id[0].toString()]])
								geaenderteAttribute.add([id:  attributIdWGrp, Wert: MapObjekt])
							}
						}
						// BME unterschiedlich?
						if ((match) && (attributBme != bme) && (attributBme != null))
						{
							MapObjekt = new HashMap()
							MapObjekt.put ("objectAttributeValues",[["value":bme]])
							geaenderteAttribute.add([id:  attributIdBme, Wert: MapObjekt])
							log.debug attributBme + " -> " + bme
						}
						// SAP Materialnummer unterschiedlich?
						if ((match) && (attributMaterialnummer != sapmaterialnummer) && (attributMaterialnummer != null))
						{
							MapObjekt = new HashMap()
							MapObjekt.put ("objectAttributeValues",[["value":sapmaterialnummer]])
							geaenderteAttribute.add([id:  attributIdMaterialnummer, Wert: MapObjekt])
							log.debug attributMaterialnummer + " -> " + sapmaterialnummer
						}
						if (geaenderteAttribute.size()>0)
						{
							log.debug geaenderteAttribute.size() + " Attribute müssen geändert werden."
						}
						// Iteration über die geänderten Attribute
						geaenderteAttribute.each
						{
							updAttribut ->
							builder = new groovy.json.JsonBuilder (updAttribut.Wert)
							connREST = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectattribute/"+ updAttribut.id.toString()).openConnection()
							connREST.setDoOutput(true)
							connREST.setRequestProperty("Content-Type", "application/json; charset=utf-8");
							connREST.setRequestProperty("Authorization", "Basic ${authString}")
							connREST.setRequestMethod("PUT")
							wr = new DataOutputStream(connREST.getOutputStream())
							wr.writeBytes(builder.toPrettyString())
							wr.flush()
							wr.close()
							Antwort = new groovy.json.JsonSlurper().parse(connREST.getInputStream())
						}
					}
      }
			if (AusfuehrungOK)
			{
				// Datenbank Status aktualisieren
				log.debug "Aktualisierung vom Datensatz für " + sapmaterialnummer
				sql.execute (statementFlagSetzen,[sapmaterialnummer])
			}
			else
			{
				// Datensatz bekommt eine noch zu implementierende Sonderbehandlung 
				log.debug "Aktualisierung vom Datensatz für " + sapmaterialnummer +". Wird als fehlerhaft markiert."
				def statementFehlerFlagSetzen = """update sm.SAP_MATERIALSTAMM set FLAG_AKTUALISIERUNG=9 where MATERIALNR=?"""
				sql.execute (statementFehlerFlagSetzen,[sapmaterialnummer])
			}
    }
    else
    {
      log.error "Mehr als ein Suchtreffer! Es wurden " + sapMaterialInsight.totalFilterCount + " Objekte gefunden."
    }
  }
}
else
{
    log.debug "Keine zu bearbeitenden Materialstämme gefunden."
}
