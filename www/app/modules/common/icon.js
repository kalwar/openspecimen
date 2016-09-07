
angular.module('openspecimen')
  .directive('osSpecimenIcon', function($http, ApiUrls) {
    function linker(scope, element, attrs) {
      scope.prop = {};

      if (scope.value) {
        var url = ApiUrls.getBaseUrl() + '/specimen-units/icon?value=' + scope.value;
        $http.get(url).then(
          function(prop) {
            scope.prop = prop.data;
          }
        );
      }
    }

    return {
      restrict: 'E',
      replace: true,
      link : linker,
      scope:{
        value: '='
      },
      template: '<span ng-switch on="!!prop.abbreviation">' +
                '  <span ng-switch-when="true" class="os-specimen-icon">{{prop.abbreviation | limitTo : 2}}</span>' +
                '  <span ng-switch-default class="{{prop.icon}}"></span>' +
                '</span>'
    };
  });
