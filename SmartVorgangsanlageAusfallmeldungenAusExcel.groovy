/*
KN 18.5.2016
Funktionsbeschreibung: Wir suchen uns alle Ausfallmeldungen in der Tabelle sm.qm_ausfallmeldungen, zu denen in sm.qm_ausfallmeldungen_smart kein Vorgang angelegt ist (Schlüssel Chargennummer und Datum)
Prüfung 1: Gibt es die Charge in Smart, sonst Eintrag mit Fehlermeldung erzeugen
 */
import groovy.sql.Sql
import java.sql.Driver
import java.text.DecimalFormat
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

def dfDatum = new SimpleDateFormat("YYYY-MM-DD")

def int AnzahlChargen = 0

def abfrageArbeitsvorrat = """
  select 
    to_char(datum,'YYYY-MM-DD') datum,
    CHARGENNR,
    gewicht,
    fehlerbeschreibung,
    FOTOS_VORHANDEN,
    ausfallort,
    entscheidung
  from sm.QM_AUSFALLMELDUNGEN qa
  where not exists (select * from sm.QM_AUSFALLMELDUNGEN_SMART qas where qas.datum=qa.DATUM and qas.chargennr=qa.CHARGENNR) and
    chargennr is not null and
    rownum<11
"""

def statementEinfuegenMeldedaten = """
  insert into sm.qm_ausfallmeldungen_smart (datum, chargennr, jira_key, jira_id, meldung) values (to_date(?,'YYYY-MM-DD'),?,?,?,?) 
"""

def String QueryChargenVorlage = """
{ "page": 1, "asc": 1, "objectTypeId": 84, "filters": [ { "objectTypeAttributeId": 135, "selectedValues": [ "@@Chargennr@@" ] } ], "resultsPerPage": 9999, "includeAttributes": true }
"""
def String QueryChargen = ""

rsArbeitsvorrat = sql.rows (abfrageArbeitsvorrat)

if (rsArbeitsvorrat.isEmpty()==false) {
  def MapVorgang = new HashMap()
  def MapFields = new HashMap()
  def MapFieldcontent = new HashMap()
  def ListItems = new ArrayList()
  def MapObjekt = new HashMap()

  def slurper = new groovy.json.JsonSlurper()

  def String EntscheidungKey = ""
  def String AnlageKey = ""
  def String ChargeKey = ""
  def String Meldung = ""
  rsArbeitsvorrat.each {ch ->
    EntscheidungKey = ""
    AnlageKey = ""
    ChargeKey=""
    Meldung = ""
    log.debug ch.chargennr
    // Teil 1: Chargennummer in Insight nachschlagen
    HttpURLConnection connRESTChargen = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/object/navlist").openConnection()
    connRESTChargen.setDoOutput(true)
    connRESTChargen.setRequestProperty("Content-Type", "application/json")
    connRESTChargen.setRequestProperty("Authorization", "Basic ${authString}")
    wrChargen = new DataOutputStream(connRESTChargen.getOutputStream())
    QueryChargen = QueryChargenVorlage.replace("@@Chargennr@@",ch.chargennr)
    wrChargen.writeBytes(QueryChargen)
    wrChargen.flush()
    wrChargen.close()
    RESTChargen = new groovy.json.JsonSlurper().parse(connRESTChargen.getInputStream())    
    AnzahlChargen = Integer.parseInt(RESTChargen.totalFilterCount.toString())
    if (AnzahlChargen==0) {
      log.debug "Charge nicht angelegt"
      sql.execute (statementEinfuegenMeldedaten,[ch.datum, ch.chargennr,null,null,"Charge in Insight nicht angelegt."])
    }
    else {
      ChargeKey = RESTChargen.objectEntries[0].objectKey.toString()
      // Nachschlagen: Materialbewertung
      switch (ch.entscheidung) {
	case "abgeschnitten + Faktor angefragt ok": EntscheidungKey="SM-108504"; break
	case "abgeschnitten kl. Fa ok": EntscheidungKey="SM-108504"; break
	case "abgeschnitten ok": EntscheidungKey="SM-108504"; break
	case "abgeschnitten": EntscheidungKey="SM-108504"; break
	case "an der Beize kontrollieren": EntscheidungKey="SM-107873"; break
	case "an der Schere abschneiden": EntscheidungKey="SM-108504"; break
	case "ASG informiert": EntscheidungKey="SM-121526"; break
	case "auf 810mm vorbesäumen": EntscheidungKey="SM-108504"; break
	case "Besäumt auf 715mm mit 20mm Saum und 100mm Schrottstreifen": EntscheidungKey="SM-108504"; break
	case "ca. 10m abgeschnitten": EntscheidungKey="SM-108504"; break
	case "die letzten 35m abschneiden": EntscheidungKey="SM-108504"; break
	case "Ergebnis von 45139/2R abwarten": EntscheidungKey="SM-107873"; break
	case "Fräsen und ausliefern": EntscheidungKey="SM-107875"; break
	case "für  4,004mm genommen": EntscheidungKey="SM-107874"; break
	case "geschnitten": EntscheidungKey="SM-108504"; break
	case "geschnitten, wird bei ASG nachgearbeitet": EntscheidungKey="SM-108504"; break
	case "Kundenfreigabe liegt vor": EntscheidungKey="SM-121526"; break
	case "lt. H. Kalz ok": EntscheidungKey="SM-107873"; break
	case "nochmal gefräst, weiter fertigen und unter Aufsicht H. Reinke beizen": EntscheidungKey="SM-107875"; break
	case "nachgebeizt ok": EntscheidungKey="SM-107875"; break
	case "ok": EntscheidungKey="SM-107873"; break
	case "OK H. Reinke": EntscheidungKey="SM-107873"; break
	case "ok lt. H. Reinke": EntscheidungKey="SM-107873"; break
	case "Ring gekennzeichnet schicken, Kunde ist informiert": EntscheidungKey="SM-121526"; break
	case "Ring wurde an Schere 2- statt 3-fach geschnitten": EntscheidungKey="SM-108504"; break
	case "Schneideinteilung liegt vor": EntscheidungKey="SM-108504"; break
        case "Schrott": EntscheidungKey="SM-107876"; break
	case "sind ohne Probleme gewalzt": EntscheidungKey="SM-107873"; break
	case "Sonderfreigabe liegt vor": EntscheidungKey="SM-121526"; break
	case "umgewickelt und abgeschnitten": EntscheidungKey="SM-108504"; break
        case "verschrottet": EntscheidungKey="SM-107876"; break
        case "verschrottet ": EntscheidungKey="SM-107876"; break
	case "versenden nach Rücksprache": EntscheidungKey="SM-121526"; break
	case "weiterfertigen H. Reinke": EntscheidungKey="SM-107875"; break
	case "weiterfertigen und unter Aufsicht H. Reinke schneiden": EntscheidungKey="SM-107875"; break
	default:
	  Meldung="Materialbewertung nicht erkannt"
	  EntscheidungKey="SM-107871"
      }
      // Nachschlagen: Anlage
      switch (ch.ausfallort) {
	case "Beize": AnlageKey="SM-121531"; break
	case "Fräse": AnlageKey="SM-108482"; break
	case "Fräse ": AnlageKey="SM-108482"; break
        case "Gießanl1": AnlageKey="SM-121562"; break
        case "Gießanl2": AnlageKey="SM-121563"; break
        case "Gießanl3": AnlageKey="SM-121542"; break
        case "Gießanl3/10": AnlageKey="SM-121542"; break
        case "Gießanl3/11": AnlageKey="SM-121519"; break
	case "Haube": AnlageKey="SM-121876"; break
	case "Kaltwalze": AnlageKey="SM-121532"; break
	case "Mino": AnlageKey="SM-121532"; break
        case "Säge": AnlageKey="SM-121533"; break
        case "Schere 1": AnlageKey="SM-121875"; break
        case "Schere2": AnlageKey="SM-121519"; break
        case "Schere 2": AnlageKey="SM-121519"; break
        case "Schere 3": AnlageKey="SM-121534"; break
	case "Versand": AnlageKey="SM-121530"; break
	case "Warmwalze": AnlageKey="SM-121541"; break
      }
      if ((EntscheidungKey=="") || (AnlageKey=="")) {
        if (AnlageKey=="") {
          sql.execute (statementEinfuegenMeldedaten,[ch.datum, ch.chargennr,null,null,"Schlüssel Anlage nicht erkannt: "+ch.ausfallort])
	}
        else {
          sql.execute (statementEinfuegenMeldedaten,[ch.datum, ch.chargennr,null,null,"Schlüssel Entscheidung nicht erkannt: "+ch.entscheidung])
	}
      }
      else {
      // Los geht's: Vorgang anlegen
      MapFieldcontent.put ("key","PM")
      MapFields.put ("project",MapFieldcontent)
      MapFieldcontent = new HashMap()
      MapFieldcontent.put ("name","Ausfallmeldung")
      MapFields.put("issuetype",MapFieldcontent)
      MapFields.put("summary","Ausfallmeldung") //wird durch workflow ersetzt
      MapFields.put("description",ch.fehlerbeschreibung)
      // Produkt Materialbewertung: cf_10519
      MapObjekt = new HashMap()
      ListItems = new ArrayList()
      MapObjekt.put ("key",EntscheidungKey) //Schlüssel aus Nachschlageaktion oben
      ListItems.add(MapObjekt)
      MapFields.put ("customfield_10519",ListItems)
      MapVorgang.put ("fields",MapFields) 
      // Zeitstempel: cf_10403
       MapFields.put("customfield_10403",ch.datum+"T12:00:00.000+0100")
       // Chargen: cf_10200
       MapObjekt = new HashMap()
       ListItems = new ArrayList()
       MapObjekt.put ("key",ChargeKey)
       ListItems.add(MapObjekt)
       MapFields.put ("customfield_10200",ListItems) 
	// Anlage: cf_10526
       MapObjekt = new HashMap()
       ListItems = new ArrayList()
       MapObjekt.put ("key",AnlageKey)
       ListItems.add(MapObjekt)
       MapFields.put ("customfield_10526",ListItems) 
       MapVorgang.put ("fields",MapFields) 
       // Gewicht: cf_11704
       MapFields.put("customfield_11704",ch.gewicht)
       MapVorgang.put ("fields",MapFields) 
       // Fotos vorhanden: cf_11705
       if (ch.fotos_vorhanden.intValue()==1) {
         MapFields.put("customfield_11705","ja")
       }
       else {
         MapFields.put("customfield_11705","nein")
       }
       MapVorgang.put ("fields",MapFields) 
       

       builder = new groovy.json.JsonBuilder (MapVorgang)
       //log.debug builder.toPrettyString()
       HttpURLConnection connCreate = new URL(Constants.BASE_URL_SMART + "/rest/api/2/issue/").openConnection()
       connCreate.setDoOutput(true)
       connCreate.setRequestProperty("Content-Type", "application/json")
       connCreate.setRequestProperty("Authorization", "Basic ${authString}")
       wrCreate = new DataOutputStream(connCreate.getOutputStream())
       wrCreate.writeBytes(builder.toPrettyString())
       wrCreate.flush()
       wrCreate.close()

       try {response=connCreate.getInputStream()}
       catch (java.io.IOException e) {}
       Vorgang = slurper.parse(response)
       log.debug Vorgang.key
       sql.execute (statementEinfuegenMeldedaten,[ch.datum, ch.chargennr,Vorgang.key,Vorgang.id,Meldung])	
     }    
   }
  }  
}
else {
  log.debug "Keine neuen Ausfallmeldungen anzulegen"
}


