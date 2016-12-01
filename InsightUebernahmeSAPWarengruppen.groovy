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

//Datenbankverbindung Oracle
def driver = Class.forName('oracle.jdbc.OracleDriver').newInstance() as Driver

def props = new Properties()
props.setProperty("user", "sm")
props.setProperty("password", "sonsdihdf")

def conn = driver.connect("jdbc:oracle:thin:@oraprod:1521:FS4P", props)
def sql = new Sql(conn)

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()

String AbfrageArbeitsvorrat = """
   select 
     wgrp,
     name_1,
     name_2
  from SAP_WARENGRUPPEN 
  where FLAG_AKTUALISIERUNG=1
"""

String statementAktualisierungZuruecksetzen = """
    update sm.sap_warengruppen set flag_aktualisierung=0 where wgrp=?
  """


def rsArbeitsvorrat = sql.rows (AbfrageArbeitsvorrat)

//log.debug rsArbeitsvorrat

String VorlageQueryWarengruppe = """
{ "page": 1, "asc": 1, "objectTypeId": 3, "filters": [ { "objectTypeAttributeId": 15, "selectedValues": [ "@@SAPWarengruppe@@" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
"""

if (rsArbeitsvorrat.isEmpty()==false) {
    rsArbeitsvorrat.each {wgrp ->
        log.debug wgrp.wgrp.toString()
	
	//Eigenschaten Warengruppe vorbesetzen
	ArrayList attribute = []
	attribute.add ([name:"Name", typ:"String", objectTypeAttributeId:11, attributIdInsight:null, inhaltDB:wgrp.wgrp.toString() + " | " + wgrp.name_1.toString(), inhaltInsight:"", flagAktualisiert:0])
	attribute.add ([name:"WGrp", typ:"String", objectTypeAttributeId:15, attributIdInsight:null, inhaltDB:wgrp.wgrp.toString(), inhaltInsight:"", flagAktualisiert:0])
	attribute.add ([name:"Name_1", typ:"String", objectTypeAttributeId:16, attributIdInsight:null, inhaltDB:wgrp.name_1.toString(), inhaltInsight:"", flagAktualisiert:0])
	attribute.add ([name:"Name_2", typ:"String", objectTypeAttributeId:17, attributIdInsight:null, inhaltDB:wgrp.name_2.toString(), inhaltInsight:"", flagAktualisiert:0])
	
	log.debug attribute
	
	// Abfrage existierende Warengruppe in Insight
	QuerySAPWarengruppe = VorlageQueryWarengruppe.replace("@@SAPWarengruppe@@",wgrp.wgrp.toString())
	HttpURLConnection connSAPWgrp = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
	connSAPWgrp.setDoOutput(true)
	connSAPWgrp.setRequestProperty("Content-Type", "application/json");
	connSAPWgrp.setRequestProperty("Authorization", "Basic ${authString}")
	wrSAPWgrp = new DataOutputStream(connSAPWgrp.getOutputStream())
	wrSAPWgrp.writeBytes(QuerySAPWarengruppe)
	wrSAPWgrp.flush()
	wrSAPWgrp.close()
	InsightSAPWgrp = new groovy.json.JsonSlurper().parse(connSAPWgrp.getInputStream())
	
	// Wgrp in Insight vorhanden
	if (InsightSAPWgrp.totalFilterCount==1) {
	  // Vorhandene Wgrp aktualisiert
	    log.debug "Aktualisieren: Wgrp " + wgrp.wgrp
	    //Wir besorgen uns die aktuell im Insight vorhandenen Daten
	    InsightSAPWgrp.objectEntries[0].attributes.each {attributInsight ->
	      attribute.each {attribut ->
	        if (attributInsight.objectTypeAttributeId==attribut.objectTypeAttributeId) {
		  attribut.attributIdInsight=attributInsight.id
		  if (attribut.typ=="String") {
  		    attribut.inhaltInsight=attributInsight.objectAttributeValues[0]?.value
		  }
		  if (attribut.typ=="Referenz") {
  		    attribut.inhaltInsight=attributInsight.objectAttributeValues[0].referencedObject?.id.toString()
		  }
		  if ((attribut.inhaltDB!=attribut.inhaltInsight)) { // && (attribut.attributIdInsight!=null)
		    attribut.flagAktualisiert=1
		  }
		}
	      }
	    }
	    attribute.each {attribut ->
	      if (attribut.flagAktualisiert==1) {
	        if (attribut.attributIdInsight!=null) {
		  //log.debug attribut
	          MapAttribut=new HashMap()
		  MapAttribut.put ("objectAttributeValues",[["value":attribut.inhaltDB]])
                  builderAttribut = new groovy.json.JsonBuilder (MapAttribut)
                  connAttribut = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectattribute/"+attribut.attributIdInsight.toString()).openConnection()
                  connAttribut.setDoOutput(true)
                  connAttribut.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                  connAttribut.setRequestProperty("Authorization", "Basic ${authString}")
                  connAttribut.setRequestMethod("PUT")
                  wrAttribut = new DataOutputStream(connAttribut.getOutputStream())
                  //log.debug builderAttribut.toPrettyString()
                  wrAttribut.writeBytes(builderAttribut.toPrettyString())
                  wrAttribut.flush()
                  wrAttribut.close()
                  AntwortAttribut = new groovy.json.JsonSlurper().parse(connAttribut.getInputStream())
	          //log.debug AntwortAttribut
                  log.debug  "Attribut " + attribut.attributIdInsight.toString() + " aktualisiert"
		}
                else {
	          MapAttribut=new HashMap()
		  MapAttribut.put ("objectId",InsightSAPWgrp.objectEntries[0].id)
		  MapAttribut.put ("objectTypeAttributeId",attribut.objectTypeAttributeId)
		  MapAttribut.put ("objectAttributeValues",[["value":attribut.inhaltDB]])
                  builderAttribut = new groovy.json.JsonBuilder (MapAttribut)
		  log.debug builderAttribut.toPrettyString()
                  connAttribut = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectattribute/create").openConnection()
                  connAttribut.setDoOutput(true)
                  connAttribut.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                  connAttribut.setRequestProperty("Authorization", "Basic ${authString}")
                  connAttribut.setRequestMethod("POST")
                  wrAttribut = new DataOutputStream(connAttribut.getOutputStream())
                  wrAttribut.writeBytes(builderAttribut.toPrettyString())
                  wrAttribut.flush()
                  wrAttribut.close()
                  AntwortAttribut = new groovy.json.JsonSlurper().parse(connAttribut.getInputStream())
	          //log.debug AntwortAttribut
                  log.debug  "Attribut angelegt"
                }		
	     }
	   }
	}
	// Warengruppe in Insight nicht vorhanden
	else {
	    AttributeRoh = []
	    attribute.each {attribut ->
	      AttributeRoh.add([id:attribut.objectTypeAttributeId, value:attribut.inhaltDB])
	    }
            MapObjekt = new HashMap()
            MapAttribut = new HashMap()
            MapWert = new HashMap()

            MapObjekt.put ("objectTypeId",3)
            def ListAttribute=[]
            AttributeRoh.each {attribut ->
              MapAttribut = ["objectTypeAttributeId": attribut.id]
              MapWert = ["value": attribut.value]
              MapAttribut.put ("objectAttributeValues",[MapWert])
              ListAttribute.add(MapAttribut)
            }
            MapObjekt.put ("attributes",ListAttribute)

            builderAnlage = new groovy.json.JsonBuilder (MapObjekt)
	    //log.debug builderAnlage.toPrettyString()
	
            connAnlage = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
            connAnlage.setDoOutput(true)
            connAnlage.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connAnlage.setRequestProperty("Authorization", "Basic ${authString}")
	    wrAnlage = new DataOutputStream(connAnlage.getOutputStream())
	    wrAnlage.writeBytes(builderAnlage.toPrettyString())
	    wrAnlage.flush()
	    wrAnlage.close()
	    WarengruppeInsight = new groovy.json.JsonSlurper().parse(connAnlage.getInputStream())
	  }
    sql.execute (statementAktualisierungZuruecksetzen,[wgrp.wgrp])
    }
}
else {
    log.debug "Keine aktualisierten Warengruppen gefunden."
}

