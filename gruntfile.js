'use strict';
//This version number will be used in  jenkins build and packaging
var version = "1.0.0";
var globalGrunt = require('..\\build\\grunt\\gruntfile.Main.js');

var config = {
		solutions:
		{
			// The proj file is not required, but using a value here since this would be required for archiva upload
			sln: "InfoGridjavautility.proj",
			version: "1.0.0",
			tests:{
				 //Added test template sample
			     path:[""],
				 //Type will allow us to support multi-types, mstest,nunit etc.
				 type: ""	
			},
			packagefolder:{
				//If the packagefolder.path is left blank by default it takes the Release folder as default.
				path 		:"\\releasejavautility",
				// Name of the package can be specified by the user here.
				packagename	:"igjavautility"
			}
		}
		
};

module.exports = function (grunt) {
    globalGrunt.init(grunt, config);
};
