/*
 * Copyright 2013-2015 Mikhail Shugay (mikhail.shugay@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.milaboratory.migec

import java.util.zip.GZIPInputStream

import static com.milaboratory.migec.Util.getBLANK_PATH

//========================
//          CLI
//========================
def cli = new CliBuilder(usage: "BacktrackSequence [options] query_sequence assembled.fastq[.gz] " +
        "[alignment_file.asm (generated by Assemble with --alignment-details) or $BLANK_PATH] output")
def scriptName = getClass().canonicalName
def opt = cli.parse(args)
if (opt == null || opt.arguments().size() < 4) {
    cli.usage()
    System.exit(2)
}

//========================
//         PARAMS
//========================
String query = opt.arguments()[0].toString()
def aInputFileName = opt.arguments()[1],
    rInputFileName = opt.arguments()[2] == BLANK_PATH ? null : opt.arguments()[2],
    outputFileName = opt.arguments()[3]

//========================
//      MISC UTILS
//========================
def getReader = { String fname ->
    new BufferedReader(new InputStreamReader(fname.endsWith(".gz") ? new GZIPInputStream(new FileInputStream(fname)) :
            new FileInputStream(fname)))
}

def getUmiEntry = { String header ->
    def splitHeader = header.split(" ")
    def umiEntry = splitHeader.find { it.startsWith("UMI:") }
    if (umiEntry == null) {
        println "[${new Date()} $scriptName] Error: no UMI header in input. Terminating"
        System.exit(2)
    }
    umiEntry.split(":")
}

//========================
//          BODY
//========================
// Step1 - collect UMIs
def umiConsensusData = new HashMap<String, String>()

def reader = getReader(aInputFileName)
String header, seq, qual
int totalUmis = 0, totalReads = 0
println "[${new Date()} $scriptName] Reading assembled data from $aInputFileName, collecting UMI headers"
while ((header = reader.readLine()) != null) {
    if (!header.startsWith("@")) {
        println "[${new Date()} $scriptName] Not a FASTQ!"
        System.exit(2)
    }
    seq = reader.readLine()
    reader.readLine()
    qual = reader.readLine()

    if (seq.contains(query)) {
        def umiEntry = getUmiEntry(header)
        totalUmis++
        totalReads += umiEntry.length > 2 ? Integer.parseInt(umiEntry[2]) : 1
        umiConsensusData.put(umiEntry[1], seq + "\n+\n" + qual + "\n+\n")
    }
}

// Step2 - if consensus alignment data file is specified, append data from it
if (rInputFileName) {
    println "[${new Date()} $scriptName] Reading assembly details, collecting alignments for UMIs"
    reader = getReader(rInputFileName)
    def umiHeader = "", line
    String alignment = null
    while ((line = reader.readLine()) != null) {
        if (line.startsWith("@")) {
            if (alignment)
                umiConsensusData.put(umiHeader, alignment) // add info for prev UMI

            umiHeader = line.substring(1)
            alignment = umiConsensusData.get(umiHeader) // add consensus to alignment, or return null if not our UMI
        } else if (alignment && line.length() > 0)
            alignment += line + "\n"  // append if our UMI
    }
}

// Step3 - report
println "[${new Date()} $scriptName] Finished"
new File(outputFileName).withPrintWriter { pw ->
    pw.println(">Query: $query")
    pw.println(">UMIs found: $totalUmis")
    pw.println(">Reads in MIGs: $totalReads")
    pw.println(">UMI data:")
    umiConsensusData.each {
        pw.println("@" + it.key)
        if (rInputFileName)
            pw.println(it.value)
    }
}