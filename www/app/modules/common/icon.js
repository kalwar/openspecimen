
angular.module('openspecimen')
  .directive('osIcon', function($http, ApiUrls) {
    function linker(scope, element, attrs) {
      scope.prop = {};

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
          scope.prop = prop.data;
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
      template: '<span ng-switch on="!!prop.abbreviation">' +
                '  <span ng-switch-when="true" class="os-icon">{{prop.abbreviation | limitTo : 2}}</span>' +
                '  <span ng-switch-default class="{{prop.icon}}"></span>' +
                '</span>'
                
    };
  });
