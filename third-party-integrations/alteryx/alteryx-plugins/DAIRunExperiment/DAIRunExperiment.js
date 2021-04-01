const displayScoreSelect = () => {
    if (Alteryx.Gui.Manager.getDataItem('IsClassification').getValue()) {
        document.getElementById('scoreclassification').style.display = 'initial'
        document.getElementById('scoreregression').style.display = 'none'
    } else {
        document.getElementById('scoreclassification').style.display = 'none'
        document.getElementById('scoreregression').style.display = 'initial'
    }
}

const toggleKeycloak = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseKeycloak').getValue()) {
        document.getElementById('keycloakServerURL').style.display = 'initial'
        document.getElementById('keycloakTokenEndpoint').style.display = 'initial'
        document.getElementById('keycloakRealm').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
    } else {
        document.getElementById('keycloakServerURL').style.display = 'none'
        document.getElementById('keycloakTokenEndpoint').style.display = 'none'
        document.getElementById('keycloakRealm').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {

    const operationClassificationSelector = new AlteryxDataItems.StringSelector('ClassificationDropdown', {
        optionList: [
            {label: 'True', value: 'True'},
            {label: 'False', value: 'False'}
        ]
    })

    const operationTimeSeriesSelector = new AlteryxDataItems.StringSelector('TimeSeriesDropdown', {
        optionList: [
            {label: 'True', value: 'True'},
            {label: 'False', value: 'False'}
        ]
    })

    Alteryx.Gui.Manager.getDataItem('IsTimeSeries').setValue('False')
    manager.addDataItem(operationTimeSeriesSelector)
    manager.bindDataItemToWidget(operationTimeSeriesSelector, 'TimeSeriesDropdown')
    displayScoreSelect()
    toggleKeycloak()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayScoreSelect()
    toggleKeycloak()
    Alteryx.Gui.Manager.getDataItem('IsClassification').registerPropertyListener('value', displayScoreSelect)
    Alteryx.Gui.Manager.getDataItem('UseKeycloak').registerPropertyListener('value', toggleKeycloak)
}