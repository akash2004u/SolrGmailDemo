/**
 * @author : Akash Yadav (akash.yadav@markit.com) 
 */

var mailSearch = angular.module('mailSearch', []);

mailSearch.config(function($httpProvider) {
	  $httpProvider.defaults.useXDomain = true;
	  $httpProvider.defaults.headers.common['Access-Control-Allow-Origin'] = true;
	  delete $httpProvider.defaults.headers.common['X-Requested-With'];
	});

mailSearch.controller('indexCtrl',['$scope','indexService'
                                   ,function($scope,indexService) {
    $scope.emails = [];
    $scope.footerMessage = '';
    
    $scope.$watchCollection('searchText', function() {
    	$scope.fetchEmails();
    });
    
    $scope.fetchEmails = function(){
    	var key = '' + $scope.searchText;
    	if(key == 'undefined' || key == 'null' || key.trim().length < 1) {
    		key = '*'
    	}

   	 	$scope.footerMessage = '';
    	indexService.getEmails(key)
	     .success(function(data) {	
	    	 $scope.footerMessage = 'Query Returned '+data;
	    	 console.log(data);
	          $scope.emails = data.response.docs;
	          console.log($scope.emails);
	     })
	     .error(function() {
	        console.log('There was an error Searching');
	     });
    };
    
    $scope.getEmails = function(){
    	return $scope.emails;
    };
    
  }]);

mailSearch.service('indexService',['$http',function($http){
	var BASE_URL = 'http://localhost:8983/solr/heapwalk/select?wt=json';
	
	this.getEmails = function(key) {
		console.log('Search Key :'+key);
		
		key = key.trim()+' ';
		
		var query = "";
		var filterQuery = [];
		
		var values = key.split(' ');
		for (index = 0; index < values.length; ++index) {
			var val = values[index];
			var idxCol = val.indexOf(':');
			if(idxCol > -1  ){
				if(idxCol != val.length -1) {
					console.log('contains');
					filterQuery[filterQuery.length] = val;
				}
			} else {
				query += ' ' + val;
			}
		}
		
		console.log('Printing Query to be fired');
		console.log(query);
		console.log(filterQuery);
		
		return $http({
			method : 'JSONP',
			url : BASE_URL,
			params : {
				'json.wrf' : 'JSON_CALLBACK',
				'q' : query.trim().length < 1 ? '*' : query,
				'fq': filterQuery
			}
		});
	};
	
	
}]);

mailSearch.directive('ngEnter', function () {
    return function (scope, element, attrs) {
        element.bind("keydown keypress", function (event) {
            if(event.which === 13) {
                scope.$apply(function (){
                    scope.$eval(attrs.ngEnter);
                });

                event.preventDefault();
            }
        });
    };
});