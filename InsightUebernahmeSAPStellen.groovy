import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import java.net.URLEncoder
import java.net.URL
import Constants

if (Constants.SYSTEM != 'productive') {
        log.debug "Groovy Script kann nur auf dem Produktiv-System ausgeführt werden."
        return;
}

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()

// Liste Mitarbeiter aus REST-Service holen

def HttpURLConnection connMitarbeiterListe = new URL("http://automat01/SchichtberichteService/MitarbeiterListe").openConnection()
connMitarbeiterListe.setDoOutput(false)
connMitarbeiterListe.setRequestProperty("Content-Type", "application/json")
def MitarbeiterListe = new groovy.json.JsonSlurper().parse(connMitarbeiterListe.getInputStream())

String VorlageQueryStelle= """
{ "page": 1, "asc": 1, "objectTypeId": 281, "filters": [ { "objectTypeAttributeId": 487, "selectedValues": [ "@@Stellennummer@@" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
"""

MapStellen = new HashMap()
MitarbeiterListe.each {mitarbeiter ->
  if (mitarbeiter.Stelle!=null) {
    if (mitarbeiter.Stelle!="00000000") {
      MapStellen.put (mitarbeiter.Stelle.substring(3,8), mitarbeiter.Stelle.substring(3,8))
    }
  }
}

if (MapStellen.isEmpty()==false) {
    MapStellen.each {stelle ->
      //Eigenschaften Stelle vorbesetzen

      ArrayList attribute = []
      attribute.add ([name:"Name", typ:"String", objectTypeAttributeId:483, attributIdInsight:null, inhaltDB:stelle.value, inhaltInsight:"", flagAktualisiert:0])
      attribute.add ([name:"Stellennummer", typ:"String", objectTypeAttributeId:487, attributIdInsight:null, inhaltDB:stelle.value, inhaltInsight:"", flagAktualisiert:0])
      attribute.add ([name:"Stellenbezeichnung", typ:"String", objectTypeAttributeId:488, attributIdInsight:null, inhaltDB:"NEU", inhaltInsight:"", flagAktualisiert:0])
	
      //log.debug attribute
	
      // Abfrage existierende Mitarbeiter in Insight
      QueryStelle = VorlageQueryStelle.replace("@@Stellennummer@@",stelle.value.toString())
      HttpURLConnection connStelle= new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
      connStelle.setDoOutput(true)
      connStelle.setRequestProperty("Content-Type", "application/json");
      connStelle.setRequestProperty("Authorization", "Basic ${authString}")
      wrStelle = new DataOutputStream(connStelle.getOutputStream())
      wrStelle.writeBytes(QueryStelle)
      wrStelle.flush()
      wrStelle.close()
      InsightStelle = new groovy.json.JsonSlurper().parse(connStelle.getInputStream())
      
      // Nur Neuanlage, keine Aktualisierung im Moment
      if (InsightStelle.totalFilterCount==0) {
	log.debug "Stelle Neuanlage " + stelle
	AttributeRoh = []
	attribute.each {attribut ->
	  AttributeRoh.add([id:attribut.objectTypeAttributeId, value:attribut.inhaltDB])
	}
        MapObjekt = new HashMap()
        MapAttribut = new HashMap()
        MapWert = new HashMap()

        MapObjekt.put ("objectTypeId",281)
        def ListAttribute=[]
        AttributeRoh.each {attribut ->
          MapAttribut = ["objectTypeAttributeId": attribut.id]
          MapWert = ["value": attribut.value]
          MapAttribut.put ("objectAttributeValues",[MapWert])
          ListAttribute.add(MapAttribut)
        }
        MapObjekt.put ("attributes",ListAttribute)
        builderAnlage = new groovy.json.JsonBuilder (MapObjekt)
	log.debug builderAnlage.toPrettyString()
	
        connAnlage = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/create").openConnection()
        connAnlage.setDoOutput(true)
        connAnlage.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connAnlage.setRequestProperty("Authorization", "Basic ${authString}")
	wrAnlage = new DataOutputStream(connAnlage.getOutputStream())
	wrAnlage.writeBytes(builderAnlage.toPrettyString())
	wrAnlage.flush()
	wrAnlage.close()
	StelleInsight = new groovy.json.JsonSlurper().parse(connAnlage.getInputStream())
      }	  
   }
}
else {
    log.debug "Stellenliste ist leer!!!"
}

