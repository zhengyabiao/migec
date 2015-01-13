/**
 Copyright 2013-2014 Mikhail Shugay (mikhail.shugay@gmail.com)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.milaboratory.migec

import static com.milaboratory.migec.Util.BLANK_PATH

def cli = new CliBuilder(usage:
        "CheckoutBatch [options, see Checkout] barcode_file output_dir/'")

def opt = cli.parse(args)
if (opt == null || opt.arguments().size() < 2) {
    println "[ERROR] Too few arguments provided"
    cli.usage()
    System.exit(-1)
}

def options = args[0..-3], barcodesFileName = args[-2], outputPath = args[-1]

def scriptName = getClass().canonicalName

if (!options.contains("--append"))
    options.add("--append")

def filesHash = new HashSet<String>()
new File(barcodesFileName).splitEachLine("\t") { splitLine ->
    if (!splitLine[0].startsWith("#") && splitLine.size() > 3 && splitLine[3] != BLANK_PATH) {
        def fastq1 = splitLine[3], fastq2 = splitLine.size() > 4 ? splitLine[4] : BLANK_PATH
        filesHash.add([fastq1, fastq2].join(" "))
    }
}

println "[${new Date()} $scriptName] Will run Checkout for the following fastq pairs:\n" +
        "${filesHash.collect().join("\n")}\n" +
        "barcodes: $barcodesFileName\n" +
        "output path: $outputPath"

filesHash.each {
    Util.run(new Checkout(), [options, barcodesFileName, it, outputPath].flatten().join(" "))
}