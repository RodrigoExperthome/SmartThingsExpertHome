/**
 *  Expert Alarm
 *
 *  Version 1.0.0 (8/12/2015)
 *  Inspired in SmartAlarm by statusbits, but recoded to simplify logics, and enable 
 *  better integration with Android Tasker Keypad
 *
 *  The latest version of this file can be found on GitHub at:
 *  --------------------------------------------------------------------------
 *
 *  Copyright (c) 2015 Experthome.cl
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import groovy.json.JsonSlurper

definition(
    name: "Alarma ExpertHome",
    namespace: "Experthome",
    author: "rodrigo@experthome.cl",
    description: "Sistema de alarma integrado a Experthome.cl. Permite monitoreo de casa, " +
                 "y activación de sirenas, luces, luces, notificación sms y camaras foscam. " +
                 "Incluye integración tasker para keypad android y Alexa (via switch virtuales) y control remoto.",
    category: "Safety & Security",
    
    iconUrl: "http://experthome.cl/wp-content/uploads/2015/08/Security_14.png",
    iconX2Url: "http://experthome.cl/wp-content/uploads/2015/08/Security_14.png",
)

preferences {
    page name:"pageStatus"
    page name:"pageSensores" 
    page name:"pageOpcionesSensor" 
    page name:"pageOpcionesActivacion"
    page name:"pageOpcionesAlarma" 
    
}

def pageStatus() {
    
    def alarmStatus = "La Alarma está ${statusAlarma()}"
    def pageProperties = [
        name:       "pageStatus",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]
    return dynamicPage(pageProperties) {
        section("Estado") {
            paragraph alarmStatus
        }
        section("Setup Menu") {
            href "pageSensores", title:"Selecciona sensores", description:"Toca para abrir"
            href "pageOpcionesSensor", title:"Configura activacion", description:"Toca para abrir"
            href "pageOpcionesActivacion", title:"Opciones de activacion", description:"Toca para abrir"
            href "pageOpcionesAlarma", title:"Opciones de alarma", description:"Toca para abrir"
        }
        section([title:"Options", mobileOnly:true]) {
            label title:"Assign a name", required:false
        }
    }
}

def pageSensores() {
    log.debug("pageSensores()")
    def inputContact = [
        name:       "contacto",
        type:       "capability.contactSensor",
        title:      "Sensores de puerta/ventana",
        multiple:   true,
        required:   false
    ]
    def inputMotion = [
        name:       "movimiento",
        type:       "capability.motionSensor",
        title:      "Sensores de movimiento",
        multiple:   true,
        required:   false
    ]
    def pageProperties = [
        name:       "pageSensores",
        nextPage:   "pageStatus",
        uninstall:  false
    ]
    return dynamicPage(pageProperties) {
        section("Agrega/remueve sensores...") {
            input inputContact
            input inputMotion
        }
    }
}
def pageOpcionesSensor() {
    log.debug("pageOpcionesSensor()")
    def resumen = 
        "Cada sensor se puede configurar como Afuera o Casa." +
        "El armado Casa considera que puede haber movimiento dentro de la " +
        "casa sin generar una activacion de alarma." +
        "Cuando la alarma se arma en modo Afuera, se activan los sensores Afuera y Casa."
    def pageProperties = [
        name:       "pageOpcionesSensor",
        nextPage:   "pageStatus",
        uninstall:  false
    ]
    def tipoSensor = ["Afuera", "Casa"]
    return dynamicPage(pageProperties) {
        section("Sensores") {
            paragraph resumen
        }
        if (settings.contacto) {
            section("Sensores Contacto") {
                def devices = settings.contacto.sort {it.displayName}
                devices.each() {
                    def devId = it.id
                    def displayName = it.displayName
                    input "type_${devId}", "enum", title:displayName, metadata:[values:tipoSensor], defaultValue:"Afuera"
                }
            }
        }
        if (settings.movimiento) {
            section("Sensores Movimiento") {
                def devices = settings.movimiento.sort {it.displayName}
                devices.each() {
                    def devId = it.id
                    def displayName = it.displayName
                        input "type_${devId}", "enum", title:"${it.displayName}", metadata:[values:tipoSensor],defaultValue:"Casa"
                }
            }
        }
        section("Definir Puerta Principal...") {
            input "inputPuerta","capability.contactSensor", title:"Puerta Principal", multiple:true, required: false
            input "inputDelay", "enum", title:"Retraso en Activacion (seg)", metadata:[values:["30","45","60"]], defaultValue:"30", required: true
        }
    }    
}        
def pageOpcionesActivacion() {
    log.debug("pageOpcionesActivacion()")
    def resumen =
        "Expert Alarm se puede instalar via:" +
        "(i) android keypad (tasker), (ii) control remoto y," +
        "(iii) cambio de modo (solo para activacion Afuera)."
    def resumenRemotos =    
        "Control remoto por default define botones " +
        "(1) Afuera, (2) Casa, (3) Desactivar, (4) Panico"
    def resumenBoton =    
        "Solo para ser usados por botones simulados " + 
        "definidos en ST. Nombre botones deben ser: " +
        "armadoAfuera, armadoCasa, desarmado, panico"
    def resumenAudio =    
        "Opciones de audio"        
    def inputModoAfuera = [
        name:       "modosAfuera",
        type:       "mode",
        title:      "Activa Afuera en estos modos...",
        multiple:   true,
        required:   false
    ]
    def inputRemotes = [
        name:       "remoto",
        type:       "capability.button",
        title:      "Que control remoto?",
        multiple:   true,
        required:   false
    ]
    def inputSwitchAfuera = [
        name:       "switchAfuera",
        type:       "capability.switch",
        title:      "Afuera?",
        multiple:   true,
        required:   false
    ]
    def inputSwitchCasa = [
        name:       "switchCasa",
        type:       "capability.switch",
        title:      "En Casa?",
        multiple:   true,
        required:   false
    ]
    def inputSwitchDesactivar = [
        name:       "switchDesactivar",
        type:       "capability.switch",
        title:      "Desarmado?",
        multiple:   true,
        required:   false
    ]
    def inputSwitchPanico = [
        name:       "switchPanico",
        type:       "capability.switch",
        title:      "Panico?",
        multiple:   true,
        required:   false
    ]
    def pageProperties = [
        name:       "pageOpcionesActivacion",
        nextPage:   "pageStatus",
        uninstall:  false
    ]
    return dynamicPage(pageProperties) {
        section("Opciones de Armado") {
            paragraph resumen
        }
        section("Modos") {
            input inputModoAfuera
        }
        section("Controles Remotos") {
           paragraph resumenRemotos
           input inputRemotes
        }
        section("Switch Simulados para Keypad + Alexa") {
           paragraph resumenBoton
           input inputSwitchAfuera
           input inputSwitchCasa
           input inputSwitchDesactivar
           input inputSwitchPanico
        }
    }
}
def pageOpcionesAlarma() {
    log.debug("pageOpcionesAlarma()")
    def resumen =
        "Acciones a realizar despues de una activación." +
        "Incluye sirenas, luces, fotos, y notificaciones. " +
        "Notificaciones push (activacion & cambio estado) " +
        "se envian de manera automatica."
    def inputSirena = [
        name:           "sirena",
        type:           "capability.alarm",
        title:          "Que sirenas?",
        multiple:       true,
        required:       false
    ]
    def inputLuces = [
        name:           "luces",
        type:           "capability.switch",
        title:          "Que luces?",
        multiple:       true,
        required:       false
    ]
    def inputCamaras = [
        name:           "camaras",
        type:           "capability.imageCapture",
        title:          "Que camaras?",
        multiple:       true,
        required:       false
    ]
    def inputPhone1 = [
        name:           "phone1",
        type:           "phone",
        title:          "Envia a este numero",
        required:       false
    ]
    def inputPhone2 = [
        name:           "phone2",
        type:           "phone",
        title:          "Envia a este numero",
        required:       false
    ]
    def inputPushbulletDevice = [
        name:           "pushbullet",
        type:           "device.pushbullet",
        title:          "Que cuenta pushbullet?",
        multiple:       true,
        required:       false
    ]
    def inputAudioPlayers = [
        name:           "audioPlayer",
        type:           "capability.musicPlayer",
        title:          "Que parlantes?",
        multiple:       true,
        required:       false
    ]
    def inputSpeechTextArmedAway = [
        name:           "speechTextArmedAway",
        type:           "text",
        title:          "Frase Armado Afuera",
        required:       false
    ]
    def inputSpeechTextArmedStay = [
        name:           "speechTextArmedStay",
        type:           "text",
        title:          "Frase Armado En Casa",
        required:       false
    ]
    def inputSpeechTextDisarmed = [
        name:           "speechTextDisarmed",
        type:           "text",
        title:          "Frase Desarmado",
        required:       false
    ]
    def pageProperties = [
        name:       "pageOpcionesAlarma",
        nextPage:   "pageStatus",
        uninstall:  false
    ]

    return dynamicPage(pageProperties) {
        section("Opciones de Alarma") {
            paragraph resumen
        }
        section("Sirenas") {
            input inputSirena
        }
        section("Luces a prender") {
            input inputLuces
        }
        section("Camaras") {
            input inputCamaras
        }
        section("Mensajes de Texto") {
            input inputPhone1
            input inputPhone2
        }
        /** No me queda claro que sea usado en configuracion normal.
        section("Pushbullet") {
            input inputPushbulletDevice
        }
        section("Audio") {
            input inputAudioPlayers
            input inputSpeechTextArmedAway
            input inputSpeechTextArmedStay
            input inputSpeechTextDisarmed
        }
        */
    }
}

def installed() {
    log.debug("installed()")
    initialize()
}

def updated() {
    log.debug("updated()")
    unschedule()
    unsubscribe()
    initialize()
}

private def initialize() {
    //Estado inicial de alarma
    state.afuera = false
    state.casa = false
    state.panico = false
    state.desarmado = true
    //Mapeo y revision de la alarma
    state.alarmaOn = false
    state.offSwitches = []
    //Mapeo sensores y suscripcion a eventos
    log.debug("${statusAlarma()}")
    sensores()
    controlRemoto()
    switchSimulado()
}

//mapeo sensores y suscripcion
private def sensores() {
    log.debug("sensores()")
    state.sensorContacto = []
    state.sensorContacto << [
        idSensor:   null,
        tipoArmado: "Afuera",
    ]
    if (settings.contacto) {
        settings.contacto.each() {
            state.sensorContacto << [
                idSensor:   it.id,
                tipoArmado: settings["type_${it.id}"] ?: "Afuera",
            ]
        }
        subscribe(settings.contacto, "contact.open", onContacto)
        state.sensorContacto.each() {
            log.debug ("${it.idSensor} / ${it.tipoArmado}")    
        }
    }
    state.sensorMovimiento = []
    state.sensorMovimiento << [
        idSensor:   null,
        tipoArmado: "Casa",
    ]
    if (settings.movimiento) {
        settings.movimiento.each() {
            state.sensorMovimiento << [
                idSensor:   it.id,
                tipoArmado: settings["type_${it.id}"] ?: "Casa",
            ]
        }
        subscribe(settings.movimiento, "motion.active", onMovimiento)
        state.sensorMovimiento.each() {
            log.debug ("${it.idSensor} / ${it.tipoArmado}")    
        }
    }
}
// Control remoto por default define botones (1) Afuera, (2) Casa, (3) Desactivar, (4) Panico
private def controlRemoto() {
    if (settings.remoto) {
        subscribe(settings.remoto, "button", onControlRemoto)
    }
}
//Nombre Switch Simulado debe ser mismo que funciones definidas
//Tiene que estar en ingles para Alexa (Away, Home & Panic). No se permite desactivacion por Alexa.
//Alexa podria funcionar con botones simulados como antes.
//Problema es mantener actualizado a Tasker, especialmente en armados fallidos.
private def switchSimulado() {
    log.debug("switchSimulado()")
    if (settings.switchAfuera) {
        subscribe(settings.switchAfuera,"switch.on",onSwitchSimulado)
    }
    if (settings.switchCasa) {
        subscribe(settings.switchCasa,"switch.on",onSwitchSimulado)
    }
    if (settings.switchDesactivar) {
        subscribe(settings.switchDesactivar,"switch.on",onSwitchSimulado)
    }
    if (settings.switchPanico) {
        subscribe(settings.switchPanico,"switch.on",onSwitchSimulado)
    }
}
//Cuando ocurre un evento de contact.open, reviso 
//que tipo de armado tiene el sensor, y lo comparo con el
//estado de la alarma.
//** Falta implementar un delay (inputDelay) para la puerta principal (inputPuerta)
def onContacto(evt) {
    log.debug("Evento ${evt.displayName} / ${evt.deviceId}")
    def contactoOk = state.sensorContacto.find() {it.idSensor == evt.deviceId}
    if (!contactoOk) {
        log.warn ("No se encuentra el dispositivo de contacto ${evt.deviceId}")
        return
    }
    //Pensar en usar atomicState....
    if((contactoOk.tipoArmado = "Afuera" && state.afuera) || (contactoOk.tipoArmado = "Casa" && state.afuera)
    || (contactoOk.tipoArmado = "Afuera" && state.casa)) {
        log.debug("Activando Alarma ${evt.displayName}")
        activarAlarma(evt.displayName)    
    }
}
//Cuando ocurre un evento de motion.presence, reviso 
//que tipo de armado tiene el sensor, y lo comparo con el
//estado de la alarma.
//No existe delay en este caso, dado que siempre tiene que sonar la alarma
def onMovimiento(evt) {
    log.debug("Evento ${evt.displayName} / ${evt.deviceId}")
    def movimientoOk = state.sensorMovimiento.find() {it.idSensor == evt.deviceId}
    if (!movimientoOk) {
        log.warn ("No se encuentra el dispositivo de movimiento ${evt.deviceId}")
        return
    }
    //Pensar en usar atomicState....
    if((movimientoOk.tipoArmado == "Afuera" && state.afuera) || (movimientoOk.tipoArmado == "Casa" && state.afuera)
    || (movimientoOk.tipoArmado == "Afuera" && state.casa)) {
        log.debug("Activando Alarma ${evt.displayName}")
        activarAlarma(evt.displayName)    
    }
}
//Cuando se aprieta un boton del control remoto, 
//se ejecuta un cambio en el estado de la alarma.
def onControlRemoto(evt) {
    log.debug("onControlRemoto")
    if (!evt.data) {
        return
    }
    def slurper = new JsonSlurper()
    def data = slurper.parseText(evt.data)
    def button = data.buttonNumber?.toInteger()
    if (button) {
        log.debug("Boton ${button} fue ${evt.value}")
        //Nombre en ingles para integracion con Alexa
        if (button == 1) {
            away()
        } else if (button==2) {
            home()
        } else if (button==3) {
            disarm()
        } else if (button==4) {
            panic()
        }
        
    }
}
//Nombre Switch Simulado debe ser mismo que funciones definidas
def onSwitchSimulado(evt) {
    "${evt.displayName}"()
}

private def away() {
    log.debug("Preparando Armado Afuera")
    if (revisarContactos() && !atomicState.afuera && !atomicState.alarmaOn){
        armadoAlarma(true)
    } else {
        
        log.debug("Armado no exitoso")
        
    }
}
private def home() {
    log.debug("Preparando armadoCasa")
    if (revisarContactos() && !atomicState.casa && !atomicState.alarmaOn){
        armadoAlarma(false)
    } 
}
private def disarm() {
    log.debug("Preparando desarmado")
    if (!atomicState.desarmado){
        desactivarAlarma()
    } 
}
private def panic() {
    log.debug("panico")
    log.debug("Alarma ya esta sonando!!!! ${state.alarmaOn}")
    if (!atomicState.panico && !state.alarmaOn){
        activarPanico()
         //mensaje push avisando que es boton de panico!!!
    } 
}
//Falta push y SMS. Pensar como integrar en Tasker.
private def activarAlarma(nombreDispositivo) {
    log.debug("BEE DO BEE DO BEE DO")
    state.alarmaOn = true
    settings.sirena*.strobe()
    settings.camaras*.take()
    def lucesOn = settings.luces?.findAll {it?.latestValue("switch").contains("off")}
    log.debug("lucesOn: ${lucesOn}")
    if (lucesOn) {
        lucesOn*.on()
        state.offLuces = lucesOn.collect {it.id}
    }
    //Implementar mensaje tipo push y SMS. Pensar como realizarlo en Tasker.
    def msg = "Alarma en ${location.name}! - ${nombreDispositivo}"
    log.debug("${msg}")
}

private def activarPanico() {
    log.debug("PANICO!!!!")
    state.alarmaOn = true
    settings.sirena*.strobe()
    settings.camaras*.take()
    def lucesOn = settings.luces?.findAll {it?.latestValue("switch").contains("off")}
    log.debug("lucesOn: ${lucesOn}")
    if (lucesOn) {
        lucesOn*.on()
        state.offLuces = lucesOn.collect {it.id}
    }
    //Implementar mensaje tipo push y SMS. Pensar como realizarlo en Tasker.
    def msg = "Alarma en ${location.name}! - PANICO"
    log.debug("${msg}")
}

private def desactivarAlarma() {
    log.debug("Desarmado exitoso")
    state.afuera = false
    state.casa = false
    state.desarmado = true
    state.panico = false
    
    state.alarmaOn = false
    settings.sirena*.off()
    def lucesOff = state.offLuces
    if (lucesOff) {
        log.debug("lucesOff: ${lucesOff}")
        settings.luces?.each() {
            if (lucesOff.contains(it.id)) {
                it.off()
            }
        }
        state.offLuces = []
    }
    log.debug("Afuera: ${state.afuera}/Casa: ${state.casa}/Desarmada: ${state.desarmado}/Panico: ${state.panico}")
    //Implementar mensaje tipo push y SMS
}
//Armado Afuera = true y Armado Casa = false
private def armadoAlarma(tipo){
    state.desarmado = false
    state.panico = false
    if (tipo){
        state.afuera = true
        state.casa = false
    } else {
        state.afuera = false
        state.casa = true
    }
    //Cambio de estado de switches, ie, es necesario actualizar switches
    
    
    //Implementar mensaje tipo push. Pensar como realizarlo en Tasker.
    if (tipo){
        log.debug("Alarma esta armada Afuera")
    } else {
        log.debug("Alarma esta armada Casa")
    }
    log.debug("Afuera: ${state.afuera}/Casa: ${state.casa}/Desarmada: ${state.desarmado}/Panico: ${state.panico}")
}
//Falta mandar un msg explicando razon de porque no se pudo armar la alarma
private def revisarContactos(){
    def puertaAbierta = settings.contacto.findAll {it?.latestValue("contact").contains("open")}
    if (puertaAbierta.size() > 0) {
        puertAbierta.each() {
            log.debug("${it.displayName} esta abierto, no se puede continuar con proceso armado")    
        }
        return false
        //implementar mensaje tipo push. Pensar como realizarlo en Tasker.
    }
    return true
}

//Actualiza estado de switch simulados
//Ojo con doble suscripcion....
private def modificaSwitchSimulado(armedAway,armedHome,notArmed,panicState) {
    if (armedAway) {
        settings.switchAfuera.on()
    }else {
        settings.switchAfuera.off()
    }
    
    if (armedHome) {
        settings.switchCasa.on()
    }else {
        settings.switchCasa.off()
    }
    
    if (notArmed) {
        settings.switchDesactivar.on()
    }else {
        settings.switchDesactivar.off()
    }
   
   if (panicState) {
        settings.switchPanico.on()
    }else {
        settings.switchPanico.off()
    }
}

private def statusAlarma(){
    def statusAlarmaAhora
    if(state.afuera) {
        statusAlarmaAhora = "Status - Armada Afuera"
    }
    if(state.casa) {
        statusAlarmaAhora = "Status - Armada En Casa"
    }
    if(state.desarmado) {
        statusAlarmaAhora = "Status - Desarmada"
    }
    if(state.panico) {
        statusAlarmaAhora = "Status - Panico"
    }
    def alarmaDesinstalada = !state.afuera||!state.casa||!state.desarmado||!state.panico
    if (alarmaDesintalada==null) {
        statusAlarmaAhora = "No instalada"
    }
    return statusAlarmaAhora
}