import groovy.sql.Sql
import java.sql.Driver
import java.text.SimpleDateFormat
import Constants;

def driver = Class.forName('oracle.jdbc.OracleDriver').newInstance() as Driver

def props = new Properties()
props.setProperty("user", "sm")
props.setProperty("password", "sonsdihdf")

def conn = driver.connect("jdbc:oracle:thin:@oraprod:1521:FS4P", props)
def sql = new Sql(conn)

def authString = "rest.robot:D)P5LLt+L(*[T&BY".getBytes().encodeBase64().toString()


def AbfrageArbeitsvorrat = """
  select 
    CHARGENNUMMER,
    jira_id
  from sm.LAB_CHARGE c
  where JIRA_id is not null and
    jira_id<>0 and
    not exists (select * from DUMMY_CHARGE dc where dc.chargennummer=c.CHARGENNUMMER) and
    rownum<101
"""

String statementSchluesselAblegen = """
  insert into sm.DUMMY_CHARGE (chargennummer) values (?)
"""

rsChargen = sql.rows (AbfrageArbeitsvorrat)


def String giessanlage=""
def String gussnummer = ""
def String linkGussberichtVorlage = Constants.BASE_URL_CONFLUENCE + "/display/Giess/Gussbericht?run_1_Gussnummer=@@gussnummer@@&run_1_Anlage=@@giessanlage@@&run_1=run"
def String linkGussbericht = ""
def String linkChargenberichtVorlage = Constants.BASE_URL_CONFLUENCE + "/display/PRODWW/Chargenbericht?run_1_Chargennummer=@@chargennummer@@&run_1=run" //45297%2F2L-001
def String chargennummerURL = ""
def String linkChargenbericht = ""
def Boolean istEigenguss=false
def String jsonAttributanlageVorlage = """
{
    "objectId": @@objectId@@,
    "objectTypeAttributeId": @@objectTypeAttributeId@@,
    "objectAttributeValues": [{
        "value": "@@objectAttributeValue@@"
    }]
}
"""
def String jsonAttributanlage = ""


if (rsChargen.isEmpty()==false) {
  rsChargen.each {ch ->
        log.debug ch.chargennummer
        istEigenguss=false
        if (ch.chargennummer.toString().substring(5,6)=="/") {
	  istEigenguss=true
	  giessanlage=ch.chargennummer.toString().substring(6,7)
	  gussnummer=ch.chargennummer.toString().substring(0,5)
	  linkGussbericht = linkGussberichtVorlage.replace ("@@gussnummer@@",gussnummer).replace("@@giessanlage@@",giessanlage)
	}
	chargennummerURL = ch.chargennummer.toString().replace("/","%2F")
	linkChargenbericht = linkChargenberichtVorlage.replace("@@chargennummer@@",chargennummerURL)
	
	if (istEigenguss==true) {
          connLinkGussbericht = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectattribute/create").openConnection()
          connLinkGussbericht.setDoOutput(true)
          connLinkGussbericht.setRequestProperty("Content-Type", "application/json; charset=utf-8");
          connLinkGussbericht.setRequestProperty("Authorization", "Basic ${authString}")

          wrLinkGussbericht = new DataOutputStream(connLinkGussbericht.getOutputStream())
	  jsonAttributanlage = jsonAttributanlageVorlage.replace ("@@objectId@@",ch.jira_id.intValue().toString()).replace("@@objectTypeAttributeId@@","762").replace("@@objectAttributeValue@@",linkGussbericht)
	  //log.debug jsonAttributanlage
          wrLinkGussbericht.writeBytes(jsonAttributanlage)
          wrLinkGussbericht.flush()
          LinkGussberichtInsight = new groovy.json.JsonSlurper().parse(connLinkGussbericht.getInputStream())
	}  

	//log.debug LinkGussberichtInsight.toString()
	
          connLinkChargenbericht = new URL(Constants.BASE_URL_SMART + "/rest/insight/1.0/objectattribute/create").openConnection()
          connLinkChargenbericht.setDoOutput(true)
          connLinkChargenbericht.setRequestProperty("Content-Type", "application/json; charset=utf-8");
          connLinkChargenbericht.setRequestProperty("Authorization", "Basic ${authString}")

          wrLinkChargenbericht = new DataOutputStream(connLinkChargenbericht.getOutputStream())
	  jsonAttributanlage = jsonAttributanlageVorlage.replace ("@@objectId@@",ch.jira_id.intValue().toString()).replace("@@objectTypeAttributeId@@","761").replace("@@objectAttributeValue@@",linkChargenbericht)
	  //log.debug jsonAttributanlage
          wrLinkChargenbericht.writeBytes(jsonAttributanlage)
          wrLinkChargenbericht.flush()

        LinkChargenberichtInsight = new groovy.json.JsonSlurper().parse(connLinkChargenbericht.getInputStream())
	//log.debug LinkChargenberichtInsight.toString()

	sql.execute (statementSchluesselAblegen,[ch.chargennummer])

  }	
  wrLinkGussbericht.close()
  wrLinkChargenbericht.close()

}
else {
  log.debug "Keine zu aktualisierenden Chargen gefunden"
}
