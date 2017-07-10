'use strict';
//This version number will be used in  jenkins build and packaging
var version = "1.0.0";
var globalGrunt = require('..\\build\\grunt\\gruntfile.Main.js');

var config = {
		solutions:
		{
			sln: "",
			version: "1.0.0",
			//If the packagefolder is left balnk by default it takes the Release folder as default
			packagefolder:"\\target",
			tests:{
				 //Added test template sample
			     path:[""],
				 //Type will allow us to support multi-types, mstest,nunit etc.
				 type: ""	
			},
		}
		
};

module.exports = function (grunt) {
    globalGrunt.init(grunt, config);
};
