
angular.module('openspecimen')
  .directive('osIcon', function($http, ApiUrls) {
    function linker(scope, element, attrs) {
      scope.icon = '';

      if (scope.value) {
        getIcon(scope);
      }
    }

    function initCall(value) {
      var prop = $http.get(ApiUrls.getBaseUrl() + '/specimen-units/icon?value=' + value);
      return prop;
    }

    function getIcon(scope) {
      initCall(scope.value).then(
        function(prop) {
          scope.icon = prop.data.icon;
        }
      );
    }

    return {
      restrict: 'E',
      replace: true,
      link : linker,
      scope:{
        value: '='
      },
      template: '<span>{{icon | limitTo : 2}}</span>'
    };
  });
