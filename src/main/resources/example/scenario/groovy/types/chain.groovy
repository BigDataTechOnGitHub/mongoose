package example.scenario.groovy.types

final itemOutputFile = "items_passed_through_create_read_delete_chain.csv".toString()

// limit the whole chain step execution time by 5 minutes
// (chain step takes the limits configuration parameter values from the 1st configuration element)
final createConfig = [
    load : [
        step : [
            limit : [
                time : "5m"
            ]
        ]
    ]
]

final readConfig = [
    load : [
        type : "read"
    ]
]

// persist the items info into the output file after the last operation
final deleteConfig = [
    item : [
        output : [
            file : itemOutputFile
        ]
    ],
    load : [
        type : "delete"
    ]
]

// clean up before running the chain load step
"rm -f $itemOutputFile".execute().waitFor()

ChainLoad
    .config(createConfig)
    .config(readConfig)
    .config(deleteConfig)
    .run();