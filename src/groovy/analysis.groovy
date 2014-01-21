#!/usr/bin/groovy

import groovy.json.JsonBuilder
@groovy.lang.Grapes([
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.6' ),
    @Grab(group='commons-beanutils', module='commons-beanutils', version='1.8.3'),
    @Grab(group='log4j', module='log4j', version='1.2.17'),
    @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.5'),
    @Grab(group='org.slf4j', module='jcl-over-slf4j', version='1.7.5'),
])
import groovy.json.JsonSlurper
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.HttpResponse
import org.apache.http.impl.client.BasicCookieStore
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator


if (args.length < 1) {
	println "Usage analysis.groovy <params_filepath>"
	return 1
}

//already the default
//PropertyConfigurator.configure('log4j.properties')

def tsServer = 'http://localhost:8080/'
def ctx = 'transmart'

def err = System.err.&println
def cookieStore = new BasicCookieStore()

def http = new HTTPBuilder(tsServer)
http.client.cookieStore = cookieStore

http.request(Method.GET) {
    uri.path = "$ctx/datasetExplorer/index"
}
if (cookieStore.cookies[0].name != 'JSESSIONID') {
    err 'Failed getting JSESSIONID'
    System.exit 1
}

//def config = Eval.me(new File(args[0]).text)
def slurper = new groovy.json.JsonSlurper()
def config = slurper.parseText(new File(args[0]).text)

println config

def jobType = config['jobType']

http.request(Method.POST, ContentType.JSON) {
    uri.path = "$ctx/asyncJob/createnewjob"
    uri.query = [ 'jobType': jobType ]

    response.success = { resp, json ->
        assert resp.statusLine.statusCode == 200
        jobName = json.jobName
        assert jobName != null
        assert jobName.getClass() == String
    }

    response.failure = { resp ->
        err "Failed creating new job: $resp"
        System.exit 1
    }
}

config['jobName'] = jobName
config['analysis'] = jobType[0].toLowerCase() + jobType.substring(1)

http.request(Method.POST, ContentType.JSON) {
    uri.path = "$ctx/RModules/scheduleJob"
    uri.query = config

    response.success = { resp ->
        println 'All done for job ' + jobName
    }

    response.failure = { HttpResponse resp ->
        err "Failed scheduling the job " + jobName
        err resp.statusLine
        err resp.entity.content.text
        System.exit 1
    }
}
